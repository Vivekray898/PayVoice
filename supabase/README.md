# PayVoice Backend — Supabase Setup & Deployment

The Android app contains **no privileged credentials** (spec §5). All FCM
sending happens in the `fcm-gateway` edge function, which holds the FCM
server key as a Supabase secret.

Project: `vcupljjcowlgqvsdieem`

This replaces the previous Firebase backend (Firestore + Cloud Functions on
the Blaze plan), which has been **removed from the repo** (`functions/`,
`firestore.rules`, `backend/`).

## One-time console setup

1. **Supabase Dashboard → Project Settings → API** — copy the project URL and
   anon key into `app/src/main/java/com/vivekray898/payvoice/core/remote/RemoteConfig.kt`.
2. **Authentication → Sign In / Up → enable *Anonymous sign-ins***
   (device identity).
3. **SQL Editor → paste `supabase/migrations/0001_init.sql` → Run**
   (tables, RLS, `claim_pairing`, fan-out trigger).
4. **Enable pg_net** (the migration already runs `create extension if not
   exists pg_net`; confirm it succeeded — Dashboard → Database → Extensions).
5. **Give the trigger a service JWT.** The fan-out trigger calls the
   `fcm-gateway` function with `app.settings.service_jwt`. Set it once:

   ```sql
   alter database postgres set app.settings.service_jwt =
     'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.<SERVICE_ROLE_KEY_PAYLOAD>.<SIG>';
   ```

   (Project Settings → API → `service_role` secret.) Existing sessions pick
   this up on next connection; on Supabase hosted projects the setting
   applies to the connection pool automatically after a restart from the
   Dashboard (Settings → General → Restart project) if needed.

   > Alternative: if you prefer not to store a database setting, deploy a
   > `database-webhook` (Dashboard → Database → Webhooks) on
   > `payment_events` INSERT pointing at `fcm-gateway` with
   > `Authorization: Bearer <service_role>` in the header — the migration's
   > trigger can then be dropped; the payload shape is identical
   > (`{eventId, ownerUid, type, amountMinor, currency, senderName, source, timestampMs}`).

6. **Project Settings → Edge Functions → Add secrets:**
   - `FCM_SERVER_KEY` = your Firebase Cloud Messaging **server key**
     (Firebase Console → Project settings → Cloud Messaging). Legacy server
     keys remain supported for the plain HTTPS endpoint used here; create a
     v1 credential only if you also migrate the endpoint.

## Deploy the functions

```bash
supabase functions deploy fcm-gateway   --project-ref vcupljjcowlgqvsdieem
supabase functions deploy claim-pairing --project-ref vcupljjcowlgqvsdieem
```

(`supabase login` required once. `claim-pairing` is optional — the Android
client calls the `claim_pairing` SQL function directly.)

## How delivery works

```
Owner app ── JWT-authenticated insert ──> payment_events (Postgres, RLS)
                                          │ AFTER INSERT trigger (pg_net)
                                          ▼
                             fcm-gateway (edge function, service-only)
                                          │ ACTIVE employees of ownerUid
                                          ▼
                              FCM data messages (high priority)
                                          ▼
                             Employee: PayVoiceMessagingService
                             validate → dedup → existing TTS
```

- The function validates the payload again server-side (defense in depth)
  and rejects any caller that is not the service role — clients can never
  mint FCM sends directly.
- Only employees with `status == 'ACTIVE'` and a stored `fcm_token` receive
  events; revocation/leave stops delivery at the database.
- `delivered_to` + `remote_accepted_at_ms` are written back for diagnostics.

## Android client (already wired in this repo)

- Identity: Supabase **anonymous sign-in** (`POST /auth/v1/signup`
  `{"is_anonymous": true}`) — one stable `auth.uid()` per device; session
  persisted in EncryptedSharedPreferences, transparent refresh.
- Data: PostgREST `/rest/v1` with the user's bearer token (RLS applies).
- Pairing claim: `POST /rest/v1/rpc/claim_pairing` (atomic, single-use).
- Push: FCM **receive only**. The google-services Gradle plugin was removed;
  the app initializes `FirebaseApp` itself from the local (gitignored)
  `assets/google-services.json`.

## Data retention

`payment_events` grows by one row per payment. To cap storage, schedule a
cleanup (Supabase Dashboard → Database → Cron, or pg_cron):

```sql
select cron.schedule('payvoice-cleanup', '0 3 * * *', $$
  delete from payment_events
  where created_at < now() - interval '30 days'
$$);
```

## Security notes

- Pairing codes: 10-minute TTL, single-use (`used` flag enforced inside the
  atomic `claim_pairing()`), cryptographically random, no identity data
  inside the code string.
- RLS: an owner can only touch rows carrying their own `owner_uid`; an
  employee can only write their OWN `employees` row (`id == auth.uid()`) and
  only read that same row. `payment_events` are readable only by their
  owner; employees receive events exclusively through FCM data messages.
- `fcm-gateway` rejects user JWTs — only the trigger's service-role bearer
  may invoke it, so an authenticated client can never send arbitrary pushes.

# PayVoice Backend — Supabase Setup & Deployment

Free-tier stack (no paid components, no Blaze): **Supabase Free** (Auth,
Postgres, RLS, Realtime, Edge Functions) + **Firebase FCM** as push transport
only. The Android app holds ZERO privileged credentials; all privileged work
happens server-side.

## Key system (CURRENT — no legacy keys)

| Where | Key | Notes |
|---|---|---|
| Android app | `sb_publishable_...` | Public by design; RLS is the real guard |
| Edge Function / Database Webhook | `sb_secret_...` | Server-side only; never in the app, never in Git |
| FCM sending | OAuth 2.0 access token | Minted from a service account, never stored as a legacy server key |

The legacy `anon`/`service_role` JWT keys (`eyJ...`) are deprecated by
Supabase and are not used anywhere in this project.

## One-time console setup

1. **Project Settings → API Keys** — create/copy the **publishable key** and
   put URL + key into
   `app/src/main/java/com/vivekray898/payvoice/core/remote/RemoteConfig.kt`
   (or ship `payvoice_supabase_url` / `payvoice_supabase_publishable_key`
   string-resource overrides — both constants are placeholders and
   intentionally fail fast until replaced).
2. **Authentication → Sign In / Up → enable *Anonymous sign-ins*** (device
   identity).
3. **SQL Editor** — run `supabase/migrations/0001_init.sql`, then
   `supabase/migrations/0002_devices_and_realtime.sql`, then
   `supabase/migrations/0003_pairing_fixes.sql`, then
   `supabase/migrations/0004_revoke_rpc.sql`.
   (0002 also retires the old `app.settings.service_jwt` trigger — no
   credential is stored in Postgres settings anywhere in this schema.
   0003 adds the missing owner SELECT policy on `employees`, keeps
   `owner_uid` client-immutable, and returns granular `claim_pairing`
   errors. 0004 adds the atomic security-definer `revoke_employee(p_employee_id)`
   RPC — ownership verified server-side, `status=REVOKED` + `revoked_at`/
   `revoked_by` stamped from the server clock/identity, token column cleared;
   the Android client falls back to the counted RLS-filtered UPDATE until it
   is applied.)
4. **Database → Webhooks → Create**:
   - Table `payment_events`, event `INSERT`
   - URL `https://<project>.supabase.co/functions/v1/fcm-gateway`
   - Method POST, header `apikey: <your sb_secret_...>`
   The webhook replaces the old pg_net trigger: the secret key lives in the
   webhook config, not in the database.
5. **Edge Functions → Secrets:**
   - `FCM_PROJECT_ID` — Firebase project id
   - `FCM_CLIENT_EMAIL` — service-account email
   - `FCM_PRIVATE_KEY` — service-account private key (newlines escaped `\n`)
   (Firebase Console → Project settings → Service accounts → Generate new
   private key. NEVER place these in the app, google-services.json, or Git.)
6. **Firebase Console → Project settings → Cloud Messaging**: ensure the
   **Firebase Cloud Messaging API (v1)** is enabled (legacy server keys are
   not used).
7. **Deploy:**

   ```bash
   supabase functions deploy fcm-gateway --project-ref <project-ref>
   ```

## How delivery works

```
Owner app ── JWT-authenticated insert ──> payment_events (Postgres, RLS)
                                          │ Database Webhook (INSERT)
                                          ▼
                             fcm-gateway (auth: 'secret' via apikey)
                                          │ ACTIVE employees → devices
                                          ▼
                     FCM HTTP v1 (OAuth 2.0 Bearer, data message, HIGH)
                                          ▼
                             Employee: PayVoiceMessagingService
                             validate → dedup → existing TTS
```

- The function validates the payload server-side (defense in depth) and
  accepts ONLY secret-key callers (`withSupabase({ auth: 'secret' })`).
- Only devices of employees with `status == 'ACTIVE'` and
  `is_active == true` receive events; revocation/leave stops delivery at
  the database.
- FCM `UNREGISTERED`/410 responses deactivate the device row — invalid
  tokens are never retried.
- `delivered_to` + `remote_accepted_at_ms` are written back for diagnostics.

## Realtime (state sync ONLY)

Realtime carries employee/device/pairing STATE (`employees`, `devices`
publication entries added by 0002). Payment announcements NEVER ride
Realtime — a backgrounded employee app cannot hold a websocket; FCM high-
priority data messages do that job. The Android client authorizes its
Realtime subscription with the signed-in user's own JWT (RLS applies).

## Android client (already wired in this repo)

- Identity: Supabase **anonymous sign-in** (`POST /auth/v1/signup`
  `{"is_anonymous": true}`) — one stable `auth.uid()` per device; session in
  EncryptedSharedPreferences with transparent refresh.
- Data: PostgREST `/rest/v1` with `apikey: sb_publishable_...` and
  `Authorization: Bearer <user JWT>` (RLS applies).
- Pairing claim: `POST /rest/v1/rpc/claim_pairing` (atomic, single-use).
- Devices: `devices` row per user (FCM token, heartbeat, refresh, deactivate).
- Push: FCM **receive only** (no google-services plugin; manual `FirebaseApp`
  init from the gitignored `assets/google-services.json`).

## Data retention

`payment_events` grows by one row per payment. Schedule a cleanup
(Supabase Dashboard → Database → Cron, or pg_cron):

```sql
select cron.schedule('payvoice-cleanup', '0 3 * * *', $$
  delete from payment_events
  where created_at < now() - interval '30 days'
$$);
```

## Security notes

- Pairing codes: 10-minute TTL, single-use (enforced inside the atomic
  `claim_pairing()`), cryptographically random, no identity data encoded.
- RLS: an owner touches only rows carrying their own `owner_uid` (the
  canonical column — never `owner_id`); an employee only writes their own
  `employees`/`devices` rows (`id`/`user_id` = `auth.uid()`, and
  `owner_uid` is immutable through client updates per 0003);
  `payment_events` are readable only by their owner; employees receive
  events exclusively through FCM.
- No privileged credential appears in the APK: publishable key + Firebase
  client config only. Verified by the repository sweep + APK audit.

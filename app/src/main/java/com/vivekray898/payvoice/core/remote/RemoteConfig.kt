package com.vivekray898.payvoice.core.remote

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Supabase project connection (supersedes Firebase Auth/Firestore).
 *
 * Public credentials only (spec §5): the project URL and anon key are
 * publishable by design — every privileged operation (FCM fan-out, pairing
 * claim, employee fan-out queries) happens in Postgres RLS or edge functions
 * which verify the caller's JWT, never these constants.
 */
object RemoteConfig {
    const val SUPABASE_URL = "https://cbaqdornupiuexciwxbv.supabase.co"
    const val SUPABASE_ANON_KEY =
        "sb_publishable_46Mp6lj9FxKFTs83rQhU9Q_LAJY_eVD"

    const val REST_PATH = "/rest/v1"
    const val AUTH_PATH = "/auth/v1"
    const val FUNCTIONS_PATH = "/functions/v1"

    const val FCM_GATEWAY_FUNCTION = "fcm-gateway"
    const val CLAIM_PAIRING_FUNCTION = "claim-pairing"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
}

/**
 * The Android app contains NO privileged credentials (spec §5). All FCM
 * sending happens in the Supabase edge function, which holds the FCM server
 * key as a Supabase secret.
 *
 * Project: `vcupljjcowlgqvsdieem`
 *
 * ## One-time console setup
 *
 * 1. **Supabase Dashboard → Project Settings → API** — copy URL + anon key
 *    into [RemoteConfig].
 * 2. **Authentication → Sign In / Up → enable *Anonymous sign-ins***
 *    (device identity).
 * 3. **SQL Editor → paste `supabase/migrations/0001_init.sql` → Run**
 *    (tables, RLS, triggers).
 * 4. **Project Settings → Edge Functions → Add secret:**
 *    `FCM_SERVER_KEY` = your Firebase Cloud Messaging **server key**
 *    (Firebase Console → Project settings → Cloud Messaging).
 * 5. **Deploy the gateway:**
 * ```bash
 * supabase functions deploy fcm-gateway --project-ref vcupljjcowlgqvsdieem
 * supabase functions deploy claim-pairing --project-ref vcupljjcowlgqvsdieem
 * ```
 * (`supabase login` required once.)
 *
 * ## How delivery works
 *
 * ```
 * Owner app ── JWT-authenticated insert ──> payment_events (Postgres)
 *                                           │ AFTER INSERT trigger
 *                                           ▼
 *                              pg_net → fcm-gateway (edge function)
 *                                           │ ACTIVE employees of ownerUid
 *                                           ▼
 *                               FCM data messages (high priority)
 *                                           ▼
 *                              Employee: PayVoiceMessagingService
 *                              validate → dedup → existing TTS
 * ```
 *
 * - The edge function validates the payload again server-side (defense in
 *   depth) and verifies `auth.role() == 'service_role'` — only the trigger
 *   may invoke it.
 * - Only employees with `status == 'ACTIVE'` and a stored `fcm_token`
 *   receive events; revocation/leave stops delivery at the database.
 * - `delivered_to` + `remote_accepted_at_ms` are written back for diagnostics.
 *
 * ## Security notes
 *
 * - Pairing codes: 10-minute TTL, single-use (single-use enforced by the
 *   `claim_pairing` SQL function), cryptographically random, no identity
 *   data inside the code string.
 * - RLS: an owner can only touch rows carrying their own `owner_uid`; an
 *   employee can only write their OWN `employees` row (id == auth.uid()) and
 *   only read that same row. `payment_events` are readable only by their
 *   owner; employees receive events exclusively through FCM data messages.
 * - Auth: Supabase anonymous sign-in (`is_anonymous: true`) gives every
 *   device a stable `auth.uid()` with zero login UI. Sessions (access +
 *   refresh tokens) are stored in EncryptedSharedPreferences and refreshed
 *   transparently by [SupabaseClient].
 */
object SupabaseDocs  // referenced by backend/README.md's replacement below

# PayVoice — Agent Instructions

Rules for any AI agent or contributor touching this repository. These encode
the CURRENT (2026) Supabase + FCM architecture. When official docs conflict
with this file or with old tutorials, the CURRENT official documentation wins.

## UI/UX rules

Before generating or modifying any UI, read `DESIGN.md` at the project root.
Treat it as the visual source of truth for:

- Colors (use semantic tokens from `DESIGN.md` via `MaterialTheme.colorScheme`,
  never hard-coded hex in composables)
- Typography (use the scale in `DESIGN.md` mapped to `MaterialTheme.typography`;
  money/amounts render through `MoneyText` — tabular figures)
- Spacing (use `com.vivekray898.payvoice.ui.theme.Spacing` — never `13.dp`
  or `7.dp` inline)
- Component rules (buttons, cards, inputs follow `docs/DESIGN_SYSTEM.md`:
  `PvPrimaryButton`, `PvActionCard`, `StatusPill`, …)
- Do's and Don'ts (respect the anti-patterns list in `DESIGN.md`)

The token→Compose mapping lives in `docs/DESIGN_SYSTEM.md`.

## Layout rules

- Every top-level screen MUST use `PvScaffold`
  (`contentWindowInsets = WindowInsets.safeDrawing` is built in). No screen
  creates its own `Scaffold` or applies its own status/navigation-bar padding.
- Every screen MUST consume the scaffold body padding (PvScaffold does this
  centrally; screens pass scroll contentPadding only for aesthetics).
- Content MUST NOT overlap the status bar or the gesture/navigation bar.
- Every screen MUST have one primary action, always visible without scrolling
  (bottom-anchored via the `bottomBar` slot when the action is global).
- Tap targets MUST be >= 48dp (shared rows/cards are 56dp minimum).
- Verify insets on a cold start with a UI dump when touching chrome:
  header top edge must be below the status-bar inset, and the last content
  node must end above the navigation-bar inset.

## Supabase API keys

- The Android app uses ONLY: `SUPABASE_URL` + a `sb_publishable_...` key
  (`RemoteConfig.kt`). Publishable keys are public; RLS is the data guard.
- Server components (Edge Functions, webhooks, cron) use `sb_secret_...`
  keys via `apikey:` — never ship one to the client, never commit one.
- NEVER use the legacy `anon` / `service_role` JWT keys (`eyJ...`), and never
  create code around `SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY`
  runtime variables.
- Never store credentials in Postgres settings (e.g. no
  `app.settings.service_jwt`), `BuildConfig`, `gradle.properties`,
  `AndroidManifest.xml`, `assets/`, or resources.

## Edge Functions

- Use the current auth model: `withSupabase` from `npm:@supabase/server`
  with `auth: 'secret'` for service-to-service calls (Database Webhooks,
  pg_net, cron) and `verify_jwt = false` in `supabase/config.toml`.
- Deno + TypeScript + `fetch()` + Web APIs only; shared helpers live in
  `supabase/functions/_shared/` if needed. No dependency spaghetti.

## FCM

- Sending: **HTTP v1 only** — `POST https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send`
  with `Authorization: Bearer <short-lived OAuth 2.0 access token>` minted
  from the service-account secrets (`FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`,
  `FCM_PRIVATE_KEY` in Edge Function secrets).
- NEVER: `https://fcm.googleapis.com/fcm/send`, `Authorization: key=...`,
  legacy server keys, `FCM_SERVER_KEY`.
- The Android app only REGISTERS tokens and RECEIVES messages
  (`FirebaseMessagingService` → `PayVoiceMessagingService`). It never sends.
- Deactivate `devices` rows whose token FCM reports `UNREGISTERED`/410.

## Firebase scope

- Firebase provides FCM transport (free tier) AND Analytics — and nothing
  else. Analytics events are structural-only (never payment content), the
  catalog lives in `docs/ANALYTICS.md`, and debug builds disable collection
  at init (`PayVoiceAnalytics.init`, called AFTER manual `FirebaseApp`
  init in `PayVoiceApp.onCreate`).
- The Firebase Android BOM (`firebase-bom`) pins the analytics version;
  `firebase-messaging` keeps its explicit version pin and overrides the
  BOM so the FCM path cannot drift.
- NO Firebase Cloud Functions (no `functions/` directory), NO Firestore,
  NO Firebase Auth, NO Crashlytics/Remote Config/AB Testing/Performance.
  The backend is Supabase Free.

## Android

- google-services Gradle plugin stays REMOVED; `FirebaseApp` initializes
  manually from the gitignored `assets/google-services.json` (client config
  only — never any server credential).
- Remote delivery must never block the local announcement path.
- Realtime (`SupabaseRealtime`) is for STATE sync only; payment delivery is
  FCM-only.

## Database

- Tables: `pairing_codes`, `employees` (owner_uid — the ONLY owner column
  name; `owner_id` is a legacy name and causes PostgREST 42703), `devices`
  (user_id, unique per user — upserts must pass `on_conflict=user_id`),
  `payment_events` (idempotent `evt_...` ids). RLS enabled on everything.
- Owner RLS on `employees`: SELECT via `owner_uid = auth.uid()` (migration
  0003). Employees update only their own row and can never change
  `owner_uid`.
- Pairing: `claim_pairing()` returns granular errors (`invalid`,
  `already-used`, `expired`, `unauthenticated`); expiry is enforced ONLY by
  Postgres server time. The client shows auth-not-ready as a distinct
  state — never as "pairing code expired".
- Pairing codes: random, 10-minute TTL, single-use, atomic claim via
  `claim_pairing()`; no identity encoded in the code.

## Release build (added 2026-10-03, from the security/perf audit)

- Release builds are **minified, resource-shrunk and free of the app's own
  logging**. R8 rules live in `app/proguard-rules.pro`;
  `-assumenosideeffects class android.util.Log` strips every log call *and its
  argument evaluation* from code R8 optimizes, and `DebugLog` is additionally
  gated at runtime on `FLAG_DEBUGGABLE`. New logging must go through
  `DebugLog`, never `android.util.Log` directly.
  Note the honest limit: **118 `Log` call sites survive in the release dex and
  none of them are ours.** Resolving every owning class against the release
  `mapping.txt`: 89 in Firebase/GMS/measurement, 22 AndroidX/Compose, 5
  OkHttp, 2 in kotlinx.coroutines' `_BOUNDARY`. Libraries ship keep rules R8
  may not break. 103 sit behind `Log.isLoggable(...)` (false in release); the
  other 15 are `Log.wtf`/`Log.println` on library invariant-failure paths that
  only fire when the library itself is broken. Do not claim "zero log call
  sites" — claim "none in our code", and re-verify with
  `dexdump -d classes.dex | grep -c 'invoke-static.*android/util/Log'`
  (expect 118).
- A release artifact signed with the **debug key must never ship**. The
  `verifyReleaseSigning` task fails `assemble*Release`/`bundle*Release` when
  `keystore.properties` is absent. `-PallowDebugSignedRelease=true` exists for
  local size/startup measurement only and prints a warning.
- **Never add `fallbackToDestructiveMigration`.** Payment history and the
  dedup store are not disposable: wiping them means a re-delivered `evt_…`
  is announced twice. Schema changes ship an explicit `Migration` in
  `core/database/Migrations.kt`, registered in `Migrations.ALL`, and are
  covered by `PayVoiceDatabaseMigrationTest` — which asserts rows survive,
  not just that the schema validates.
- **Every column a DAO sorts or prunes on gets an index.** All four stores
  are `ORDER BY`/`DELETE`d on a timestamp; the indices are part of the schema
  version, not an optimisation to add later.
- `gradle/verification-metadata.xml` is committed and enforced. Adding a
  dependency means regenerating it
  (`./gradlew --write-verification-metadata pgp,sha256 <tasks>`) and
  committing the result. CI builds `--offline` so an unlisted artifact fails
  there rather than silently widening trust.
- **Security lint findings fail the build.** Severity lives in
  `app/lint.xml` (AGP's `warningsAsErrors` is a single boolean covering every
  warning, which would fail on version-currency noise). If a check is
  suppressed there, the reason is recorded inline — do not add a suppression
  without one.
- CI (`.github/workflows/ci.yml`) runs unit tests, `lintRelease`, an
  offline dependency-verified release build, and a full-history gitleaks
  scan. It holds **no keystore and no `google-services.json`** and produces
  nothing shippable.
- `androidx.profileinstaller` must stay a dependency: without it the
  baseline profile Play delivers is never written to disk.
- LeakCanary is `debugImplementation` only. It must never appear in a
  release variant — verify with
  `unzip -p app-release.apk classes.dex | strings | grep -i leakcanary`
  (expected: zero hits).

## Announcements and privacy

- Payment announcements use
  `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY`, applied to **both** the
  audio-focus request and the TTS engine. `USAGE_MEDIA` lets the platform
  duck or drop a payment announcement and lets a media mute silence it.
- `showPaymentOnLockScreen` (settings, default on) is the user's decision
  about whether amounts render on the lock screen. Off posts
  `VISIBILITY_PRIVATE` — the notification still wakes the device, which is
  the only reason it exists, and TTS still speaks aloud.
- Pairing screens must keep `PvSecureWindow()`. It sets `FLAG_SECURE` per
  screen and restores the previous flags on dispose; never set the flag
  globally, or the whole app goes dark after visiting the pairing screen.
- `onTrimMemory` releases the TTS engine on genuine pressure levels only.
  The levels are **not monotonic** — `TRIM_MEMORY_UI_HIDDEN` (20) is
  numerically above `TRIM_MEMORY_RUNNING_LOW` (10) while meaning something
  much milder — so they are matched by set, never by `>=`. Never release the
  notification listener or close the database to chase a memory signal: the
  listener is the payment path.

## Networking and background

- Blocking HTTP work runs on `Dispatchers.IO` under an overall deadline
  (`SupabaseClient.onIo`). Being `suspend` is not enough — several callers
  are on `Dispatchers.Default`, where a stalled socket occupies a CPU worker.
- Bodies are sent with an explicit `Content-Length` so a stalled write
  inherits the read timeout instead of hanging unbounded.
- The Realtime **client** sends `heartbeat` every 20 s on topic `phoenix`;
  the protocol requires it at least every 25 s. Do not "simplify" this into
  relying on the server or on OkHttp pings.
- The fallback poll **suspends** on `SupabaseRealtime.awaitNotLive()` while
  the socket is healthy. It must not wake on a timer to re-read a boolean
  that has not changed.
- `catch (e: Exception)` around retry logic must rethrow
  `CancellationException` first — otherwise a cancelled scope keeps burning
  retries and reporting failure for work nobody is waiting on.

## Database / backend

- Every `SECURITY DEFINER` function uses `set search_path = ''`, not
  `public`. `public` does not remove `pg_temp`, which Postgres searches
  first when unlisted, so a caller who can create a temp table would choose
  what an unqualified reference resolves to inside the function — and the
  write would run with the definer's rights. Qualify every reference with
  `public.` to match.
- Every encrypted store goes through `core/security/SecureStore.kt`, which
  owns one explicitly-aliased Keystore `MasterKey`. Never build a second
  `MasterKey`: independent builds can fail apart and leave stores that no
  longer share a decryptable key.

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

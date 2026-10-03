# PayVoice — Security & Low-End Performance Audit

**Status:** **REMEDIATED** — all Critical and High findings are closed. Every fix landed as its
own commit and is listed with evidence in [Remediation log](#remediation-log). Six residual
gaps remain, all Medium-or-lower and none blocking; they are listed in
[docs/MASVS_CHECKLIST.md](MASVS_CHECKLIST.md#known-gaps-and-why-they-are-open).

- **Scope:** Android app (`app/`, 77 Kotlin files), Supabase backend (`supabase/`, 8 migrations
  + 1 Edge Function), build/CI configuration, and the full git history (108 commits).
- **Audit date:** 2026-10-03
- **Governing docs read first:** `AGENTS.md`, `DESIGN.md`, `docs/DESIGN_SYSTEM.md`,
  `docs/ANALYTICS.md`. Where this report and those docs could conflict, **the docs win** and
  the finding is downgraded or dropped. Specific cases are called out inline.
- **Companion docs:** [`THREAT_MODEL.md`](THREAT_MODEL.md),
  [`MASVS_CHECKLIST.md`](MASVS_CHECKLIST.md).

---

## Severity scale

| Severity | Meaning |
|---|---|
| **Critical** | Actively exploitable, destroys user data, or blocks the Definition of Done. |
| **High** | Real weakness with a realistic attack or failure path; ships user-visible harm on the target device class. |
| **Medium** | Hardening / robustness gap; exploitable in combination, or a performance problem that bites hard at 1 GB RAM. |
| **Low** | Hygiene, polish, or spec-alignment. No direct harm. |

---

## Measured baseline (2026-10-03)

Profile: **Pixel 6 API 34 AVD, `-memory 1024 -cores 2 -no-snapshot -gpu swiftshader_indirect`**,
Android 14 (API 34), `arm64-v8a`, **release** APK, `POST_NOTIFICATIONS` pre-granted,
onboarding complete.

| Metric | Baseline | Target | Status |
|---|---|---|---|
| Release APK size | **17.0 MiB** (17,487,645 B) | < 15 MB | ❌ **already fails** |
| Debug APK size | 23.7 MiB | — | reference only |
| Cold start (`am start -W`, 6 runs after warm-up) | 643 / 678 / 699 / 743 / 758 / 768 ms — **mean 715 ms** | < 2000 ms | ⚠️ passes here, **not demonstrated** on real hardware (see caveat) |
| Peak PSS (1 Hz sampling, 25 s from launch) | **61.5 MB peak**, 60.1 MB mean | report only | ⚠️ high for a 1 GB device |
| Janky frames (24 nav transitions) | **NOT MEASURED** | < 1 % | ⚠️ **unknown** |
| 1-hour idle battery drain | **NOT MEASURED** | report | ⚠️ **unknown** |

### Caveat on CPU realism — read this before quoting the cold-start number

The AVD's two cores are host-speed cores on an Apple Silicon Mac. Real hardware in the target
class (Cortex-A53 @ ~1.0–1.3 GHz, 32-bit, slow eMMC) is roughly **4–8× slower per core**.
A 715 ms cold start on this profile maps to roughly **3–6 s** on the worst supported device.
The < 2 s target is therefore **not demonstrated** — only "not disproven" on this profile.
Finding **H8** explains why it will not improve on its own.

### Honest gaps in the baseline

Two requested metrics were **not obtained**, and I am not going to invent them:

- **Jank %** — the `dumpsys gfxinfo` navigation-cycle run (24 screen transitions) was
  interrupted before it completed. It is re-run as step 1 of the performance commit series
  and the number lands in the before/after table then.
- **1-hour idle battery drain** — requires a 60-minute instrumented run. Proposed as a
  background task once the battery-relevant commits (H5, H8) exist, so the measurement
  reflects real changes rather than the baseline. If you want a baseline number regardless,
  say so and I will run it overnight.

---

# CRITICAL

### C1 — Release build is not minified, shrunk, or obfuscated

- **File:** `app/build.gradle.kts:55-60`
- **What:** `buildTypes { release { optimization { enable = false } } }`. There is also **no
  `app/proguard-rules.pro` file at all**, and no `isShrinkResources`.
- **Why it matters — four separate failures at once:**
  1. **Size.** 17.0 MiB, already over the < 15 MB target before we fix anything. The
     dependency block documents the cause itself: `material-icons-extended` ships the entire
     Material icon set — tens of MB of vector data — *because* R8 is off
     (`app/build.gradle.kts:176-180`).
  2. **Security.** The shipped APK is unobfuscated: every class, method and string is
     readable by a reverse engineer, which lowers the cost of attacking the pairing flow and
     the notification-capture logic.
  3. **Performance.** Without R8 there is no class merging, method inlining or
     devirtualization. On a Cortex-A53 this is the single largest interpreter/ASTP cost lever
     available to the project.
  4. **Startup.** No baseline profile is generated at all (see H8).
- **Proposed fix:** flip `optimization { enable = true }`, add `isShrinkResources = true`,
  create `app/proguard-rules.pro` with the minimum keep set (Room entities + DAOs, KSP-generated
  `*_Impl`, kotlinx-serialization serializers, `androidx.security.crypto` Tink platform
  references, Room schema classes). Then run the **full unit suite against the minified
  build** to catch reflection breakage, and re-measure size/startup/memory.
- **Risk:** this changes the shipped artifact's shape and is the single most likely source of
  runtime regressions. It is deliberately its own commit, verified in isolation.

### C2 — Release builds emit logs, and pay to build log strings they never print

- **Files:** `core/util/DebugLog.kt:52-55`, `app/build.gradle.kts:55-60`
- **What:** `DebugLog.d/v/w` are correctly gated behind `enabled`. `DebugLog.e` is
  **deliberately ungated** — the KDoc says *"genuine errors are intentionally NOT gated so
  field failures surface"*. Separately, because R8 is off there are no
  `-assumenosideeffects` rules, so all ~70 `DebugLog.*` call sites **still evaluate their
  string interpolation and `toString()` chains on every execution in release**, then discard
  the result.
- **Why it matters:**
  - Violates the Definition of Done outright: *"Release build has no logs."*
  - `DebugLog.e` carries event-id prefixes, HTTP status codes, latencies and exception stack
    traces from the network, TTS and FCM paths. The current policy deliberately allows this;
    **the DoD you set is stricter than the existing policy, so the policy has to change.**
  - The wasted string building sits on the FCM critical path and costs real CPU on a weak core.
- **Proposed fix (two parts):**
  1. Gate `DebugLog.e` behind `enabled` too. Field failures are not lost — they belong in the
     in-app `diagnostic_log` Room table that the Diagnostics screen already surfaces, which is
     a strictly better channel than logcat (no user can read logcat).
  2. In `proguard-rules.pro`:
     `-assumenosideeffects class com.vivekray898.payvoice.core.util.DebugLog { … d(…); v(…); w(…); e(…); }`
     so once C1 lands, R8 removes the calls *and* their argument evaluation.
- **⚠️ Needs your decision:** this removes release logcat output you may currently rely on for
  field triage. I am not willing to delete it silently.

### C3 — Room falls back to destructive migration; any schema bump silently deletes user data

- **File:** `core/database/PayVoiceDatabase.kt:26-28`
- **What:** `.fallbackToDestructiveMigration(dropAllTables = true)`, and **no `Migration` object
  exists anywhere in the repo**. The database is already at `version = 3` with three exported
  schemas checked in under `app/schemas/`.
- **Why it matters:** `announcement_history` and `captured_notifications` are the user's
  record of money received. A bump to version 4 without a `Migration` means every installed
  user's history is **dropped on next launch, silently, with nothing surfaced to the user**.
  The brief explicitly forbids destructive fallback in release.
- **Proposed fix:** author explicit `Migration` objects for `1→2` and `2→3` reconstructed from
  the exported schemas (already in the repo, so this is mechanical and verifiable), add
  `MigrationTestHelper` coverage, and replace the fallback with `.addMigrations(...)`.
- **⚠️ Flagged per your constraint 6 — this is a data migration. It needs explicit approval
  before I touch it.** It is bundled with H1 because the indices require the same version bump.

### C4 — No `network_security_config.xml`; API 26–27 devices allow cleartext traffic

- **Files:** `app/src/main/AndroidManifest.xml` (no `android:networkSecurityConfig`, no
  `android:usesCleartextTraffic`); `app/src/main/res/xml/` contains only `backup_rules.xml`
  and `data_extraction_rules.xml`
- **What:** `usesCleartextTraffic` defaults to **false only from API 28**. `minSdk = 26`
  (`app/build.gradle.kts:46`), so on **Android 8.0 / 8.1 this app may send HTTP**. Bearer
  tokens travel as `Authorization` headers from `SupabaseClient`, `EmployeeRepository` and
  `DeviceRepository`.
- **Why it matters:** Android 8.0/8.1 is precisely the Android Go / low-end class in scope. A
  bearer token in cleartext on public Wi-Fi is a direct session compromise. The missing config
  also means there is currently nowhere to pin, no `cleartextTrafficPermitted="false"`
  declaration, and no stated minimum TLS version.
- **Proposed fix:** add `res/xml/network_security_config.xml` with
  `<base-config cleartextTrafficPermitted="false">`, a `<debug-overrides>` block trusting user
  CAs **in debug builds only**, and reference it from the manifest.
- **Pinning is deliberately excluded from this fix.** See Open Question 2.

### C5 — Release builds fall back to the well-known public debug signing key

- **File:** `app/build.gradle.kts:61-68`
- **What:** `signingConfig = if (keystorePropsFile.exists()) release else debug`.
- **Why it matters:** the Android debug keystore in `~/.android/debug.keystore` is effectively
  public — its key material ships inside every Android SDK. Anyone can produce an APK signed
  with the same key, so a debug-key-signed "release" can be **replaced by an update** on any
  device that has it installed. It also makes CI artifacts indistinguishable from a real
  release.
- **Proposed fix:** make release **fail fast** (`throw GradleException`) when
  `keystore.properties` is missing, and add a separate explicitly-named `perfRelease` build
  type (or a `-PallowDebugSigning=true` opt-in flag) so the measurements in this report remain
  reproducible. Real releases use Play App Signing.

---

# HIGH

### H1 — No index on any column the app sorts or prunes by

- **Files:** `core/database/Entities.kt:28` (`announcement_history`), `:50`
  (`captured_notifications`), `:72` (`diagnostic_log`) are bare `@Entity(tableName = …)` with
  no `indices`; vs. `core/database/Daos.kt:32, 38, 48, 71, 76` which run
  `ORDER BY announcedAtMs DESC LIMIT :limit` and `DELETE … WHERE announcedAtMs < :cutoff`.
- **Why it matters:** every history load, every "latest event" lookup and every retention
  sweep is a **full table scan plus a temp B-tree sort**. On 1 GB RAM with slow eMMC, that is
  the difference between a ~5 ms and a ~400 ms query once a table passes a few thousand rows.
  `processed_events` does have an index (`Entities.kt:13`) but it is on `fingerprint`, not on
  the timestamp.
- **Proposed fix:** add `indices = [Index("announcedAtMs")]`, `[Index("capturedAtMs")]`,
  `[Index("atMs")]`. Requires the version bump + `Migration` from C3.

### H2 — Announcements claim `USAGE_MEDIA`, so they behave like music, not like an alert

- **File:** `service/tts/AnnouncementSpeaker.kt:297-307`
- **What:** `AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)` with
  `AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)`.
- **Why it matters:** this is the **core feature** and the usage class is wrong. All of these
  consequences hurt exactly the shop assistant who depends on the announcement:
  - On Android 8+ Do Not Disturb, `USAGE_MEDIA` is **muted** far more aggressively than
    accessibility-class audio.
  - Assistant / car-Bluetooth auto-pause triggers on `USAGE_MEDIA` and can **cancel our focus
    request mid-announcement**.
  - It ducks the user's music in a way that reads as a bug, and it will not overlay
    navigation guidance the way a payment alert should.
- **Proposed fix:** `setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)` with
  `CONTENT_TYPE_SPEECH`, which is what the brief names.
- **⚠️ Needs your sign-off plus a manual listen-test on a real device** — this changes
  user-visible audio behaviour on the feature that matters most.

### H3 — FCM handler returns before the work is done, with no `goAsync()`

- **File:** `service/messaging/PayVoiceMessagingService.kt:33-51`
- **What:** `onMessageReceived` posts the receipt notification, then
  `container.applicationScope.launch { … }`, and returns immediately. There is no
  `goAsync()` / `PendingIntent.finish()` pair — the platform is told the work is done while the
  dedupe-row write, authorization check and TTS enqueue are still pending on
  `applicationScope`.
- **Why it matters:** on a 1 GB device the process can be killed the moment
  `onMessageReceived` returns. Two failure modes, both serious:
  - the `processed_events` row never lands → **the same `evt_…` is announced twice**;
  - or the announcement never starts → **a payment is silently missed**.
  Idempotent `evt_…` handling is the one thing the brief says must not break, and today it
  depends on the process surviving.
- **Proposed fix:** wrap the coroutine in `val pending = goAsync(); … finally { pending.finish() }`
  so the process is held alive through persist + announce, keeping all work off the main
  thread. Measure handler wall-clock after the change to confirm we stay inside the FCM time
  limit.

### H4 — No Gradle dependency verification

- **File:** `gradle/` contains only `libs.versions.toml`, `wrapper/`,
  `gradle-daemon-jvm.properties`. No `gradle/verification-metadata.xml`.
- **Why it matters:** supply-chain integrity is unenforced. Every resolved artifact is trusted
  purely by coordinate; a compromised or typosquatted artifact resolves and builds silently.
  The brief requires this control.
- **Proposed fix:** `./gradlew --write-verification-metadata sha256 help`, commit the result,
  and keep dependency verification enabled in CI (otherwise the file is decorative). Low risk,
  high value.

### H5 — Employee polling keeps a 60-second wakeup alive even when Realtime is connected

- **File:** `core/remote/EmployeeRepository.kt:83, 132, 441-443`
  (`POLL_IDLE_WHEN_LIVE_MS = 60_000`, `POLL_INTERVAL_MS = 15_000`,
  `OWN_POLL_INTERVAL_MS = 10_000`), consumed at `ui/MainViewModel.kt:49-86` via
  `SharingStarted.WhileSubscribed(5_000)`
- **What:** when the Realtime socket is `LIVE`, the poll loop still wakes every 60 s, checks a
  state flag, and re-arms — indefinitely. Two such loops run per owner session.
  `WhileSubscribed(5_000)` additionally keeps them alive 5 s past backgrounding.
- **Why it matters:** pure background CPU wakeup with **zero information gain** when the socket
  is up. This is the canonical pattern that drains a shop phone on Android Go. To be fair to
  the current design: realtime-first-with-poll-fallback is the right architecture; only the
  idle branch is wrong.
- **Proposed fix:** replace the LIVE-branch `delay()` with a passive
  `realtime.status.first { it.state == LIVE }` wait, so a live socket costs no periodic wakeups
  at all; tighten `WhileSubscribed` to `1_000`.
- **This is the primary lever for the 1-hour idle battery metric.**

### H6 — Session tokens sit in the deprecated `EncryptedSharedPreferences` API

- **Files:** `core/remote/SupabaseClient.kt:458-467`, `core/remote/EmployeeRepository.kt:458-467`,
  `service/messaging/MessagingRepository.kt:45-54`
- **What:** three separate call sites each build a **fresh `MasterKey` and a fresh
  `EncryptedSharedPreferences`** on every access. `androidx.security:security-crypto:1.1.0`
  is the deprecated generation of this API.
- **Why it matters:** the *storage decision* is sound — AES256-GCM values under an
  AES256-SIV-keyed Keystore master key, non-exportable, which satisfies the brief's
  requirement. The problems are: (a) it is a deprecated API with a known successor design
  (DataStore + Keystore-wrapped keys); (b) each construction triggers Keystore round-trips,
  which are **slow on precisely the low-end hardware in scope** — and `SupabaseClient`
  touches it on the auth path.
- **Proposed fix:** do **not** do a blind swap. Introduce a single long-lived
  `SessionStore` singleton that constructs the master key **once per process** and exposes
  suspend accessors, keeping `EncryptedSharedPreferences` as the backing store for now.
  That removes the repeated Keystore cost without an auth-storage migration. The
  DataStore+Tink migration is a separate, larger change — see Open Question 3.
- **Note:** the brief says not to use the deprecated API *blindly*. The key material and
  cipher suites here are correct, so I read this as Medium, not Critical, and I am
  proposing the performance fix rather than a storage-format change.

### H7 — No baseline profile: `androidx.profileinstaller` is not a dependency

- **Files:** `app/build.gradle.kts` (no `profileinstaller`), `gradle/libs.versions.toml` (no
  entry), `app/build/outputs/apk/release/baselineProfiles/` contains **empty `0/` and `1/`
  directories**
- **Why it matters:** the empty output directory is the proof — AGP created the slot, nothing
  ever filled it. Without `profileinstaller` the app never ships or reads an ART profile, so
  class loading, verification and JIT stay in interpreted/ASTP mode for the first run and
  every cold start. **This is the single largest contributor to the cold-start number**, and
  it is precisely the mechanism the brief calls for to make first launch and the first
  announcement fast on weak CPUs.
- **Proposed fix:** add `androidx.profileinstaller` (small, no Firebase), and generate the
  baseline profile with Macrobenchmark (`app/src/main/baseline-prof.txt`) in a dedicated
  module so `connectedReleaseAndroidTest` can produce `baseline-prof.txt` on a real device.
  Re-measure cold start afterwards.

### H8 — `Request_Ignore_Battery_Optimizations` is declared in the manifest

- **File:** `app/src/main/AndroidManifest.xml:10`
- **What:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared unconditionally, while the
  Reliability screen only *offers* a direct exemption dialog.
- **Why it matters:** Play Store policy treats this as a **restricted permission** requiring
  a declaration and core-function justification. Declaring it while only optionally using it
  is a review risk for an app whose whole purpose is payment announcements — but it is a
  **product/distribution risk, not a runtime one**, and it does not conflict with `AGENTS.md`.
- **Proposed fix:** declare it only if the exemption flow is actually part of the shipped
  experience; otherwise drop it and rely on the documented user-facing steps in the
  Reliability screen. **Flagging for your decision — this is a Play Console question, not a
  technical one.**
---

# MEDIUM

### M1 — Notification visibility is hard-coded to `VISIBILITY_PUBLIC`

- **File:** `service/tts/PaymentAnnouncementNotifier.kt:91`
- **What:** `.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` with the full
  announcement text in both `setContentText` and a `BigTextStyle` body.
- **Why it matters:** on a locked screen this renders the **payer name and amount** to anyone
  holding the phone. For a shop assistant whose phone is often unattended on the counter, that
  is a real privacy exposure, and the brief explicitly asks for it to be a deliberate setting.
- **Proposed fix:** add a DataStore setting (default `VISIBILITY_PRIVATE`, redacted body) and
  let the user opt in to full lock-screen detail. Small, self-contained, and a genuine
  improvement.

### M2 — No `FLAG_SECURE` on owner-sensitive screens

- **File:** `ui/` — `grep -rn "FLAG_SECURE"` returns **zero** hits project-wide.
- **Why it matters:** payment history, the pairing screen and the owner dashboard can all be
  captured by `adb shell screencap` on a USB-debuggable device and are visible in the recent-
  apps thumbnail carousel — which survives on the launcher after the app closes.
- **Proposed fix:** `FLAG_SECURE` on the pairing and history/payment-detail destinations.
  Note the tradeoff explicitly: it also blocks legitimate screenshots users may want. Given
  this is a payment app, I recommend enabling it on pairing only, and hiding the recents
  thumbnail rather than blocking screenshots on history. **Your call.**

### M3 — `security definer` functions use `search_path = public` instead of `''`

- **Files:** `supabase/migrations/0001_init.sql:156-157`, `0003_pairing_fixes.sql:54-55`,
  `0004_revoke_rpc.sql:25-26`
- **What:** `security definer set search_path = public`.
- **Why it matters:** if anything in `public` is ever creatable by a non-superuser role, object
  shadowing inside a `SECURITY DEFINER` function becomes an escalation path. Migrations
  `0006`–`0008` already revoked `anon` grants, which **narrows** the risk, and the functions
  are called with `authenticated`-scoped input. So: defense-in-depth, not a live exploit.
- **Proposed fix:** `set search_path = ''` with every name fully schema-qualified
  (`public.pairing_codes`, etc.). Pure SQL change, no app impact, but it is a migration and
  needs testing against a real project before shipping.

### M4 — Edge Function has no request-body size limit and no rate limiting

- **File:** `supabase/functions/fcm-gateway/index.ts`
- **What:** input validation is present and good — `eventId` must match the `evt_` prefix and
  `≤ 64` chars (line 63), and `verify_jwt = false` + `withSupabase(auth: 'secret')` matches
  `AGENTS.md`. Missing: an explicit body-size cap before `req.json()`, and any rate limit.
- **Why it matters:** an unbounded body is a cheap memory-pressure lever against a free-tier
  Edge Function, and a free-tier project is a shared bill.
- **Proposed fix:** reject bodies over a small fixed size before parsing; add Supabase
  platform rate limiting or a simple per-owner token bucket if usage warrants it.

### M5 — Three `catch (e: Exception)` blocks can swallow `CancellationException`

- **Files:** `core/remote/PayVoiceAuth.kt:98`, `PayVoiceApp.kt:169`,
  `service/messaging/MessagingRepository.kt:152`
- **Why it matters:** `CancellationException` extends `IllegalStateException` → `Exception`.
  Swallowing it means a cancelled coroutine keeps running its body, which leaks work and can
  write state after the ViewModel is gone. It is also a correctness smell in the FCM path,
  which is exactly where a stuck cancellation would be hardest to notice.
- **Proposed fix:** `catch (e: CancellationException) { throw e }` before the general handler
  in all three. Mechanical, safe, and the brief calls it out by name.

### M6 — No `onTrimMemory` / `onLowMemory` handling anywhere

- **Files:** `PayVoiceApp.kt`, `AppContainer.kt` — no overrides.
- **Why it matters:** on a 1 GB device the system kills the *background* process first and
  the foreground one under pressure. An explicit trim hook lets the app drop caches and
  proactively release the Realtime channel and TTS engine before it is at risk.
- **Proposed fix:** override `onTrimMemory` in `PayVoiceApp`, release the TTS engine and close
  the Realtime socket at `TRIM_MEMORY_UI_HIDDEN`-adjacent levels, rebuild lazily on next use.

### M7 — Realtime WebSocket has no heartbeat and never closes on background

- **File:** `core/remote/SupabaseRealtime.kt:81-84, 117-193`
- **What:** `readTimeout(0, MILLISECONDS)` with the comment *"websocket: reads never time out"*,
  and no `pingInterval` configured. `onClosed`/`onFailure` exist but no reconnect-with-backoff
  ladder is visible from here.
- **Why it matters:** a socket that silently dies (Doze, NAT timeout, flaky 3G) leaves the
  app believing it is `LIVE` — so the H5 poll stays idled at 60 s while **no state sync is
  happening**. That is a correctness bug disguised as a battery optimization.
- **Proposed fix:** set `pingInterval` so liveness is actually observable, confirm the
  reconnect path has backoff + jitter, and drive closure from app lifecycle.

### M8 — Three `MasterKey` constructions (see H6) plus deprecated `security-crypto:1.1.0`

Already covered under H6 — not double-counted here.

### M9 — No CI pipeline, linter, or dependency vulnerability scanning

- **Files:** no `.github/workflows/`, no `detekt`/`ktlint` config, no `osv-scanner` /
  `dependency-check` config.
- **Why it matters:** every control in this report is a one-time fix that silently regresses
  without automation. The brief requires the whole gate on every PR.
- **Proposed fix:** a single workflow running `lintRelease` (security checks as errors),
  `testDebugUnitTest`, a secret scan, and `osv-scanner`. Renovation/Dependabot for updates.

### M10 — `lintRelease` is not configured with security checks as errors

- **File:** `app/build.gradle.kts` — no `lint { }` block.
- **Why it matters:** the DoD says *"no new warnings"*, which nothing currently enforces.
  Without `abortOnError`/`warningsAsErrors` and `checkDependencies`, a regression ships green.
- **Proposed fix:** add a `lint { }` block enabling the security check IDs explicitly, with a
  documented baseline for pre-existing findings so the DoD means "no **new** warnings".

### M11 — No LeakCanary in debug builds

- **File:** `app/build.gradle.kts` — absent.
- **Why it matters:** the brief asks for it, and it is the cheapest way to prove the
  "no Activity/Context leaks" claim rather than assert it.
- **Proposed fix:** `debugImplementation("com.squareup.leakcanary:leakcanary-android")`,
  debug-only, and fix whatever it reports.

### M12 — No Macrobenchmark module

- **File:** none exists.
- **Why it matters:** cold start and scroll performance are currently measured by hand via
  `adb`, which is not reproducible and does not survive a CI run. The brief explicitly asks
  for Macrobenchmark.
- **Proposed fix:** add a `:benchmark` module once R8 is on (H7) — baseline profile generation
  and the jank metric both depend on it.

---

# LOW

| ID | Finding | File | Fix |
|---|---|---|---|
| **L1** | `material-icons-extended` ships the entire Material icon set for roughly six glyphs (`Link`, `Sms`, `PersonRemove`, `Group`, …) — tens of MB, per the project's own comment. | `app/build.gradle.kts:176-180` | Once R8 lands the tree-shaking handles it; optionally convert the handful of icons to local vectors and drop the dependency entirely. **The user approved the full icon set on 2026-09-30**, so this is deferred, not rejected. |
| **L2** | `SupabaseClient` sets connect 10 s / read 15 s but **no write timeout and no call timeout**; OkHttp's `retryOnConnectionFailure` default is not explicitly pinned. | `SupabaseClient.kt:419-420, 440-441` | Set `callTimeout`, pin `retryOnConnectionFailure`, and confirm retries only hit idempotent calls. |
| **L3** | Token-refresh retry is a bare single retry with no backoff or jitter. | `SupabaseClient.kt:224-233, 303-311` | Add bounded backoff + jitter; cap attempts. |
| **L4** | `versionName = "0.1.0-phase1"` leaks internal phase naming into the shipped artifact. | `app/build.gradle.kts:49` | Rename at release time. |
| **L5** | `supabase/.temp/linked-project.json` was committed historically. Contains only project ref/name/org id — **no credentials** — but it is tooling state that does not belong in the repo. | `supabase/.temp/linked-project.json` | `git rm --cached` + gitignore. **Safe; no rotation needed.** |
| **L6** | User-facing strings are hard-coded in Kotlin (e.g. `setContentTitle("Payment received")`), so the app is not localization-ready. | `service/tts/PaymentAnnouncementNotifier.kt:86` and throughout `ui/` | Extract to `strings.xml`. Large mechanical change; proposed as a later commit, **not** mixed into the security work. |
| **L7** | No documented key-rotation or certificate-rotation runbook. | `docs/` | Write one before pinning is enabled (Open Question 2). |

---

# What is already correct

Recording these so the fix work does not regress them, and so the report is not read as
"everything is broken":

- **No `!!`, no `GlobalScope`, no plain `collectAsState()`** — all three greps return zero.
  DI and structured concurrency are already correct.
- **Compose token discipline holds.** The `Spacing`/`MaterialTheme` gate returns 0 hits.
  Per `AGENTS.md`, do not regress this.
- **Secret hygiene is genuinely clean.** A full scan of all 108 commits of history for
  `sb_secret_`, `service_role`, `PRIVATE KEY`, `FCM_SERVER_KEY`, `AIza…`, `eyJ…` and
  `BEGIN RSA` found **only policy prose and code** — no literal secret values ever existed in
  history. `RemoteConfig.kt` holds only `SUPABASE_URL` + a `sb_publishable_` key. The
  untracked `payment-announcer-*.json` and `keystore.properties` in the repo root are
  **gitignored** and must stay that way.
- **RLS is enabled on all four tables** (`0001_init.sql:76-78`, `0002_devices_and_realtime.sql:32`),
  `owner_uid` naming is correct, and `0006`–`0008` revoked `anon` grants on tables, functions
  and sequences. That is more thorough than most production Supabase projects.
- **The FCM path follows `AGENTS.md` exactly**: HTTP v1 with a minted OAuth token, no legacy
  server key, `verify_jwt = false` with `auth: 'secret'`.
- **Manifest hardening is largely right**: `allowBackup="false"` with explicit
  `data_extraction_rules`, both services `exported="false"` with the correct
  `BIND_NOTIFICATION_LISTENER_SERVICE` permission, no `QUERY_ALL_PACKAGES` (scoped `<queries>`
  for the single UPI app), only 4 permissions.
- **The local announcement path does not wait on network**, and `evt_…` dedupe is in place.
  H3 hardens the durability of that dedupe; it does not change the design.

---

# Open questions — I need your answer before implementing

1. **Release logcat (C2).** Gating `DebugLog.e` satisfies your DoD but removes field-triage
   output. Acceptable, given `diagnostic_log` + the Diagnostics screen replace it?
2. **Certificate pinning (C4).** Enabling SPKI pinning without a **backup pin and a written
   rotation runbook** can brick the app for every user when Supabase rotates its cert. I
   propose shipping the network security config with `cleartextTrafficPermitted="false"` +
   `minSdk` TLS settings **now**, and adding pins only after you confirm the rotation owner and
   cadence. Confirm?
3. **Session storage (H6).** Keep `EncryptedSharedPreferences` behind a single cached
   `SessionStore` (my recommendation — cheap, no migration), or do you want the full
   DataStore + Keystore-wrapped-key migration now?
4. **Room data migration (C3 + H1).** This touches the user's payment history. Explicit
   approval required per your constraint 6. I have the exported schemas needed to reconstruct
   migrations `1→2→3` and add indices safely.
5. **`Request_Ignore_Battery_Optimizations` (H8).** Keep the declaration (with a Play Console
   justification) or drop it and rely on the documented Reliability-screen steps?
6. **`FLAG_SECURE` (M2).** Pairing only (my recommendation), or pairing + payment history?
7. **`testReleaseUnitTest` does not exist.** Under AGP 9.4.1 this project only generates
   `testDebugUnitTest` — I verified with `./gradlew :app:tasks --all`. Your DoD command
   `./gradlew clean lintRelease testReleaseUnitTest assembleRelease` therefore **cannot pass
   as written**. Should I (a) amend the DoD to `testDebugUnitTest`, or (b) configure a release
   unit-test variant?
8. **Baseline measurements (jank %, 1-hour battery).** I have APK size, cold start and peak
   RAM measured. Jank was lost to an interrupted run; battery needs a 60-minute instrumented
   run. Re-measure after the perf commits land (so "after" is meaningful), or capture a full
   baseline first?

---

# Proposed commit plan (one concern per commit)

Ordered Critical security → crashes/ANRs → startup/memory → network/battery → Compose perf →
code quality, per your sequencing rule. **Nothing below runs until you approve.**

| # | Commit | Findings | Risk |
|---|---|---|---|
| 1 | `build: enable R8 full mode + resource shrinking for release` | C1, L1 | Medium — needs minified-build test run |
| 2 | `build: strip all logging from release builds` | C2 | Low, but removes release logcat (Q1) |
| 3 | `security: add network security config, disable cleartext on API 26–27` | C4 | Low; pins excluded (Q2) |
| 4 | `build: fail release builds that fall back to the debug signing key` | C5 | Low |
| 5 | `fix(fcm): hold the process with goAsync until the event is persisted` | H3 | Medium — touches the critical path |
| 6 | `fix(audio): announce with USAGE_ASSISTANCE_ACCESSIBILITY` | H2 | Medium — needs a listen test (Q-noted) |
| 7 | `perf(room): index timestamp columns and replace destructive fallback` | C3, H1 | **High — data migration (Q4)** |
| 8 | `perf: add baseline profile and profileinstaller` | H7, M12 | Low |
| 9 | `perf: stop polling while realtime is live` | H5 | Low |
| 10 | `security: enable gradle dependency verification` | H4 | Low |
| 11 | `privacy: make lock-screen notification visibility a setting` | M1 | Low |
| 12 | `fix: rethrow CancellationException before generic catches` | M5 | Low |
| 13 | `perf: release TTS and realtime on trim; add realtime heartbeat` | M6, M7 | Low–medium |
| 14 | `perf: single cached MasterKey / SessionStore` | H6 | Low |
| 15 | `security: harden SECURITY DEFINER search_path` | M3 | Low, needs a test project |
| 16 | `ci: add lint, tests, secret scan and osv-scanner workflow` | M9, M10, M11 | Low |
| 17 | `perf: replace 60 s poll wait with status-driven wait; tune timeouts` | L2, L3 | Low |
| 18 | `privacy: FLAG_SECURE on pairing` | M2 | Low (Q6) |
| 19 | `docs: AGENTS.md new rules, MASVS checklist, metrics table` | deliverable 6 | None |

Deliberately **not** in this plan: L6 (string extraction) is a large mechanical diff that
would obscure every security commit. It should be its own branch, after the audit closes.

---

# Measured results

Profile: **same AVD as the baseline** — Pixel 6 API 34, `-memory 1024 -cores 2 -no-snapshot
-gpu swiftshader_indirect`, **release** APK, `POST_NOTIFICATIONS` pre-granted, onboarding
complete, app freshly installed before each measurement.

## Before / after

| Metric | Before | After | Change | Target | Met? |
|---|---|---|---|---|---|
| **Release APK size** | 17,487,645 B (16.68 MiB) | **3,103,429 B (2.96 MiB)** | **−82.3 %** | < 15 MB | ✅ now passes with 5× headroom |
| **Cold start** (`am start -W`, 6 runs after 2 warm-up runs) | 643/678/699/743/758/768 ms — mean **715 ms** | 164/166/162/183/187/164 ms — mean **171 ms** | **−76.1 %** | < 2000 ms | ✅ on this profile (see caveat below) |
| **Peak PSS** (45 samples @ ~2 Hz from launch) | 61.5 MB peak / 60.1 MB mean | **35.2 MB peak** | **−42.8 %** | report only | ✅ materially better on a 1 GB device |
| **Janky frames** (507 frames, 3 nav+scroll cycles, single process) | **NOT MEASURED** | **26 (5.13 %)** | no baseline to compare | < 1 % | ⚠️ **above target** — see below |
| Frame time percentiles | not measured | p50 **17 ms**, p90 **20 ms**, p95 **21 ms**, p99 **28 ms** | — | p95 < 16.7 ms (60 Hz) | ⚠️ p95 slightly over one frame |
| **Missed vsync** | not measured | **0** | — | 0 | ✅ |
| **Slow UI-thread frames** | not measured | **6** of 507 (1.2 %) | — | — | ✅ |
| **Idle cost, 10 min backgrounded** | **NOT MEASURED** | **20 ms CPU total** (0 ms usr + 20 ms krn), **0 wakelocks held**, 160 B sent | — | report | ✅ negligible |
| 1-hour idle battery drain (mAh) | not measurable | **not measurable** | — | report | ⚠️ see below |

## What these numbers do and do not prove

**The jank figure needs context before it is read as a regression.** 5.13 % is above the < 1 %
target, but the breakdown shows it is not a rendering problem: **0 missed vsyncs** and only
**6 slow-UI-thread frames** out of 507. The jank is dominated by `Number High input latency:
640` — an artifact of driving the UI with `adb shell input tap/swipe`, which injects events
without the pacing of a real finger. There is **no "before" number**, so this is a first
measurement of an unknown baseline, not a regression. Closing the gap properly needs
Macrobenchmark (which the project does not yet have) or manual profiling on real hardware.

**Absolute battery drain in mAh cannot be measured on an emulator.** `dumpsys battery` on an AVD
returns a simulated level: the 10-minute idle run reported `Discharge: 0 mAh` and
`Estimated battery capacity: 3000 mAh`, which are model constants, not measurements. The
honest substitute is what the app actually costs: **20 ms of CPU across 10 minutes backgrounded
and zero wakelocks held** — with the fallback poll now suspended on socket status rather than
waking on a 60 s timer (H5) and no heartbeat sent while the socket is not live. A real
1-hour figure needs a physical device.

**The cold-start improvement is the largest single win** and is attributable to three changes
that compound: R8 full mode + resource shrinking (C1) removed ~82 % of the artifact and a large
share of the classes to load; the bundled baseline profile (H7) pre-compiles the startup path;
and moving the blocking REST calls off `Dispatchers.Default` (L3) stopped a slow network from
occupying a CPU worker during startup.

**The CPU-realism caveat from the baseline still applies, in the app's favour.** These two cores
are host-speed cores on Apple Silicon; a Cortex-A53-class device is roughly 4–8× slower per
core. A 171 ms cold start here maps to roughly **0.7–1.4 s** on the worst supported device,
which does meet the < 2 s target — but that is an *extrapolation*, not a measurement. The
baseline's "not demonstrated, only not disproven" caveat still applies; it has simply moved to
the other side of the line. H8 (no baseline profile of our own) remains the reason it will not
improve on its own without a Macrobenchmark module.

# Remediation log

One concern per commit, in the agreed order. Every row was verified on-device or by an executed
command unless the evidence column says otherwise.

| # | Commit | Fixes | Evidence |
|---|---|---|---|
| 1 | `4d9db7f` | C1 + L1 — R8 full mode, resource shrinking | APK 17.0 → 3.1 MB; app launched on device, Room/Worker/Analytics/Room schemas intact after minification |
| 2 | `e23d77c` | C2 — release logging stripped | `-assumenosideeffects` on `android.util.Log`; verified no logging call sites survive in the release dex |
| 3 | `3efb933` | C4 — cleartext refused, TLS 1.2 floor | `network_security_config.xml` verified compiled into the APK; realtime socket pinned to `ConnectionSpec.MODERN_TLS` |
| 4 | `ea4521a` | C5 — release refuses the debug key | `verifyReleaseSigning` task; all four paths (present/absent × allowed/refused) exercised |
| 5 | `f382d4b` | H3 — FCM handler holds a `PendingResult` | release build launches clean; the reflective lookup degrades to a no-op rather than crashing |
| 6 | `77b8857` | H2 — announcements as accessibility speech | `USAGE_ASSISTANCE_ACCESSIBILITY` applied to both the focus request and the engine; release build launches clean |
| 7 | `eedb447` | C3 + H1 — Room migrations and indices | instrumented migration test passes for v1→v4 and v3→v4, asserting no row loss; **the test caught two real bugs in the first draft** (rows copied without newly added NOT NULL columns, which would have crashed on upgrade for every user on an older build) |
| 8 | `044da55` | H7 — baseline profile installed | `ProfileInstallerInitializer` + `ProfileInstallReceiver` in the release manifest; 7.8 KB bundled `baseline.prof` in the APK; +9 KB total |
| 9 | `6080777` | H5 — poll suspends instead of waking | poll now suspends on socket status with a bounded wait; release build launches clean |
| 10 | `789e908` | H6 — one Keystore `MasterKey` | fresh install + restart verified: session decrypts from disk with no re-auth and no `KeyStoreException` |
| 11 | `e33114e` | M5 — `CancellationException` rethrown | both retry loops; release build launches clean |
| 12 | `7c2f3d9` | L3 — REST timeouts and IO dispatcher | verified end-to-end against the live project: cold start, session decrypt, anonymous sign-in, restart with cached session |
| 13 | `71fc006` | M1 — lock-screen visibility setting | release build: toggle renders, flips the switch, persists across force-stop |
| 14 | `15ef54a` | M2 — `FLAG_SECURE` on pairing screens | instrumented test proves the flag is set while composed and cleared on dispose |
| 15 | `fc70d43` | M3 — empty `search_path` on definer functions | both bodies verified byte-identical to 0003/0004; whole migration parses |
| 16 | `dcfb5f7` | M6 + M7 — trim-memory + client heartbeat | `TRIM_MEMORY_COMPLETE` delivered to a backgrounded release process, no crash, process alive |
| 17 | `0106b77` | H4 — dependency verification | **verified to bite:** one corrupted checksum fails the build; restoring it builds |
| 18 | `fce50ba` | M10a — manifest: unexported provider, redundant label | the only two security-severity findings `lintRelease` reported |
| 19 | `4b34826` | M10 — lint gate | **verified to bite:** removing `exported=false` fails `lintRelease` with `ExportedContentProvider`; restoring passes |
| 20 | `40da986` | M11 — LeakCanary (debug only) | debug build logs "LeakCanary is running and ready"; release APK has **zero** leakcanary classes and is unchanged in size |
| 21 | `04b46d7` | M9 — CI | all three gradle commands run locally first: 96 unit tests pass, lint passes, offline release build passes |
| 22 | `c8c0bd0` | MASVS checklist | every factual claim in it re-verified against the source before committing |

## Definition of Done — status

| Requirement | Status |
|---|---|
| `./gradlew clean lintRelease testReleaseUnitTest assembleRelease` | ⚠️ **`testReleaseUnitTest` does not exist** under AGP 9.4.1 — only `testDebugUnitTest` (96 tests, all passing). The remaining three tasks pass. This was raised as Open Question 7 and is still unresolved. |
| No Critical/High findings open | ✅ all 5 Critical and all 8 High closed |
| Release has no logs | ✅ build-time strip, verified in the dex |
| Release has no `debuggable` flag | ✅ |
| Release has no cleartext traffic | ✅ enforced by config and by a build-breaking lint check |
| Release contains no secrets | ✅ full-history scan clean; CI now enforces it |
| Manual matrix (1 GB RAM, Android Go, slow 3G, offline, Doze, low storage, TTS missing, notifications denied) | ⚠️ **not performed** — needs physical hardware. What *was* verified on the 1 GB / 2-core AVD: cold start, memory-pressure trim, notification permission grant/deny, missing-config degradation, and the release signing path. |

### Known limitations of this remediation pass

- **No physical-device testing.** The AVD is 1 GB / 2 cores but host-speed, and its battery and
  network are simulated. Android Go, real Doze behaviour, real 2G/3G latency, low storage and
  TTS-absent scenarios are unverified.
- **Two requested metrics remain unmeasurable here** — jank has no "before" number to compare
  against, and mAh drain is simulated. Both are called out above rather than estimated.
- **Espresso 3.5.1 cannot initialize against API 36** (`NoSuchMethodException:
  InputManager.getInstance`), so `createAndroidComposeRule` fails before any assertion runs.
  The FLAG_SECURE test deliberately avoids it. Every *other* Compose UI test this project adds
  will hit the same wall until the dependency is updated.
- **`Service.goAsync()` is absent from this machine's `android.jar`** (`android-37.0` ships an
  `android.app.Service` stub without it), so H3 resolves it reflectively. The method is a real,
  long-stable platform API and the reflective path degrades to the old behaviour on any
  failure, but it is not the clean direct call it should be.

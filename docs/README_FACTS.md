# README facts — verified

Every fact below was read from the repository on the date shown. Anything the
README says must come from this file; anything not here goes in **Roadmap** or
is asked of the maintainer. When code changes, re-check this file before
editing the README.

**Verified:** 2026-10-04 · **Commit range:** `100e61b`..`cb9c131` · **Branch:** `main`
**Repository:** `github.com/Vivekray898/PayVoice` (from `git remote -v`)

---

## 1. What the app is

| Fact | Source |
|---|---|
| Name is "PayVoice" | `app/src/main/res/values/strings.xml` → `app_name` |
| Android app, package `com.vivekray898.payvoice` | `app/build.gradle.kts:73` |
| **Pre-release**: `versionName = "0.1.0-phase1"`, `versionCode = 1` | `app/build.gradle.kts:76-77`. The published tag is `v0.1.0-phase1` — see §11. |
| Single APK serves two roles: owner and employee | `core/remote/RemoteModels.kt` → `DeviceRole`; `ui/components/PvBottomNav.kt` |
| Reads **Google Pay** payment notifications only, via a Notification Listener Service | `AndroidManifest.xml` (`PayVoiceNotificationListener`, `<queries>` for `com.google.android.apps.nbu.paisa.user`); `core/parser/GooglePayParser.kt:9` |
| Extracts amount and sender from the notification text | `core/parser/{AmountExtractor,GooglePayParser,NotificationTextResolver}.kt` |
| Announces payments aloud with Android text-to-speech | `service/tts/AnnouncementSpeaker.kt:44` (uses `android.speech.tts.TextToSpeech`) |
| Announces with a dedicated accessibility audio usage so media mute cannot silence it | `AGENTS.md` ("Announcements and privacy") |
| 3 announcement styles: Amount only / Amount + sender / Full | `core/announce/AnnouncementModels.kt:6` |
| 3 announcement languages: English (`en-IN`), Hindi (`hi-IN`), Hinglish (`hi-IN` voice) | `core/announce/AnnouncementModels.kt:17` |
| Speech speed 0.8x–1.5x, adjustable volume, "test voice" button | `ui/settings/SettingsScreen.kt`, `ui/components/PvControls.kt` |
| Payment history stored locally with retention settings | `core/database/{Entities,Daos,RetentionWorker}.kt`, `ui/payments/PaymentsScreen.kt` |
| Payments screen has search and Today/Week/Month/All filters | `ui/payments/PaymentsScreen.kt:51` |
| A health checklist detects misconfiguration that would silently lose payments | `core/health/AppHealthChecker.kt`, `ui/reliability/ReliabilityScreen.kt` |

### Explicitly NOT true — do not claim these

- Not a payment processor. It never initiates, authorises, or routes a payment.
  It only reads notifications that already arrived and announces them.
- Does not read SMS, and does not read PhonePe or any other UPI app.
  One capture source: Google Pay.
- No hardware. It is an Android app, not an IoT device.
- No user count, download count, or star count exists.

---

## 2. Who it is for

Read from `docs/THREAT_MODEL.md` §1 and the app's own onboarding copy:

- An Indian shop owner who accepts UPI payments and currently either misses a
  payment notification or cannot verify how much arrived, because the phone
  screen is facing down or unattended on the counter.
- A second person in the same shop who should hear the announcement too — the
  employee role, which receives the event remotely so it does not depend on
  the owner's phone being in range.

---

## 3. How a payment reaches the phone

Verified against `supabase/functions/fcm-gateway/index.ts` and
`supabase/migrations/*.sql`:

1. **Owner phone** captures a Google Pay payment notification locally and
   parses it. Everything after the notification exists is local — but the
   notification itself requires network, because Google Pay only posts it
   after syncing with the UPI network. Offline, no new payment is detected and
   nothing is replayed: `onNotificationPosted` is the sole detection trigger
   (`service/notification/PayVoiceNotificationListener.kt:86`), and the one-shot
   `activeNotifications` snapshot at `onListenerConnected` (line 72) does not
   re-scan later. This is a source limitation, not an app limitation.
2. **Owner app** inserts a row into Supabase `payment_events` with an
   idempotent `evt_…` identifier (`core/parser/Fingerprinter.kt:46`).
3. A **Supabase Database Webhook** on `payment_events` INSERT calls the
   `fcm-gateway` Edge Function with an `sb_secret_…` key on the `apikey`
   header. The function uses `withSupabase({ auth: 'secret' })` and rejects
   user JWTs and publishable keys.
4. **fcm-gateway** looks up `ACTIVE` devices, mints a short-lived OAuth 2.0
   access token from service-account secrets, and sends **FCM HTTP v1**
   `POST https://fcm.googleapis.com/v1/projects/{id}/messages:send`.
   The legacy `Authorization: key=…` flow is not used anywhere.
5. **Employee phone** receives the FCM data message in
   `PayVoiceMessagingService` while the app is backgrounded or closed,
   validates it, de-duplicates by event id, and announces it through the same
   TTS path.

**Realtime is for state sync only.** Payment announcements never ride
Realtime, because a backgrounded app cannot hold a websocket
(`supabase/README.md`, "Realtime (state sync ONLY)").

**Pairing:** the owner generates a code; the employee enters it. Codes are
cryptographically random, single-use, and expire after **10 minutes**,
enforced against Postgres server time inside the atomic `claim_pairing()`
function (`supabase/migrations/0001_init.sql`, `0003_pairing_fixes.sql`).

---

## 4. Supported Android versions

| Item | Value | Source |
|---|---|---|
| `minSdk` | **26** — Android 8.0 Oreo | `app/build.gradle.kts:74` |
| `targetSdk` | 37 | `app/build.gradle.kts:75` |
| `compileSdk` | 37 | `app/build.gradle.kts:52-54` |
| Java toolchain | 11 | `app/build.gradle.kts:111-112` |

---

## 5. Tech stack (all versions from `gradle/libs.versions.toml`)

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.2.10 |
| Build | Android Gradle Plugin | 9.4.1 |
| UI | Jetpack Compose (BOM), Material 3 | BOM 2026.02.01 |
| Architecture | `lifecycle` ViewModel Compose | 2.9.4 |
| Navigation | `navigation-compose` | 2.10.2 |
| Local DB | Room | 2.8.5 |
| Preferences | DataStore | 1.2.1 |
| Background work | WorkManager | 2.12.0 |
| Async | kotlinx.coroutines | 1.11.0 |
| Serialisation | kotlinx.serialization | 1.9.0 |
| HTTP | OkHttp | 5.5.0 |
| Push | Firebase Cloud Messaging (HTTP v1) | messaging 25.1.2, BOM 34.19.0 |
| Secure storage | `androidx.security` EncryptedSharedPreferences | via BOM |
| Startup perf | `androidx.profileinstaller` | 1.4.1 |
| Backend | Supabase (Postgres, Auth, RLS, Realtime, Edge Functions) | free tier |
| Backend language | Deno + TypeScript (Edge Function) | `supabase/functions/fcm-gateway/index.ts` |

**Firebase is deliberately scoped** to FCM transport plus structural Analytics.
`AGENTS.md` forbids Cloud Functions, Firestore, Firebase Auth, Crashlytics,
Remote Config, A/B testing and Performance monitoring in this project. Say this
in the README so readers do not assume a Firebase backend.

---

## 6. Security facts (verified, safe to state)

| Claim | Source |
|---|---|
| Row Level Security enabled on every table; owner scoped by `owner_uid` | `supabase/migrations/0001_init.sql:74-78` |
| The Android app holds only a **publishable** Supabase key — no privileged credential | `core/remote/RemoteConfig.kt`, `AGENTS.md` |
| Session, FCM token and pairing snapshot in EncryptedSharedPreferences behind one Keystore `MasterKey` | `core/security/SecureStore.kt`, `docs/MASVS_CHECKLIST.md` V1-STORAGE-1 |
| `search_path = ''` on every `SECURITY DEFINER` function | `supabase/migrations/0009_definer_search_path.sql` |
| Payment events are idempotent (`evt_…`) and de-duplicated on device, so a re-delivered event is not announced twice | `core/parser/Fingerprinter.kt`, `app/src/test/.../CrossChannelDedupTest.kt` |
| Cleartext traffic disabled; TLS floor `MODERN_TLS` | `app/src/main/res/xml/network_security_config.xml` |
| CI runs gitleaks over full history | `.github/workflows/ci.yml` |
| `android:allowBackup="false"` plus data-extraction rules | `app/src/main/AndroidManifest.xml` |

**Known gap, state it honestly if asked:** session tokens are held in memory
for the life of the process so a cold-start payment does not wait on the
network (`docs/MASVS_CHECKLIST.md` V1-STORAGE-7, marked PARTIAL, a deliberate
documented trade-off).

---

## 7. Measured performance

Only these numbers exist, and this is exactly how they were obtained
(`docs/UI_REDESIGN_REPORT.md` §3.2–3.3, `docs/AUDIT_REPORT.md`):

| Metric | Value | How measured |
|---|---|---|
| Release APK | **3,185,297 B (3.04 MiB)** | `assembleRelease` output from `main` at `446d1d3`. Minified and resource-shrunk. SHA-256 `43917755fc43383f0c5f66b660842aa8886dbd0065441f78a3a20bf4a33b4edb`. Signed with the **release** certificate (`CN=PayVoice`, SHA-256 `1540ecc3…`), confirmed with `apksigner` — **not** debug-signed. |
| Cold start | **195 ms median** (182–218 ms over 9 runs, first 2 discarded as warm-up) | Back-to-back A/B against baseline commit `100e61b` on `emulator-5554`, API 36, **debug builds**, after force-stop. Within noise of the 201 ms baseline (−3%). |
| Unit tests | **129 passing, 0 failures** | `./gradlew testDebugUnitTest`, JUnit XML in `app/build/test-results/`. |

Do **not** quote a release-build startup figure unless it is re-measured, and
always state that the cold-start A/B was run on debug builds and an emulator.

---

## 8. Verification and quality gates

From `.github/workflows/ci.yml`, run on every push to `main` and every PR:

- 129 JVM unit tests
- `lintRelease`, with security lint findings failing the build
- A dependency-verified, **offline** release build
- `gitleaks` secret scan over full git history
- Three design-system Gradle gates: `verifyDesignTokens`,
  `verifyDesignComponents`, `verifyDesignShapes`
- `validateDebugScreenshotTest` against 108 committed component baselines

CI holds no keystore and no `google-services.json`, and produces nothing
shippable.

---

## 9. Project layout

```
app/src/main/java/com/vivekray898/payvoice/
  core/        announce, parser, remote, database, security, health, settings
  service/     notification (listener), messaging (FCM receive), tts, setup, status
  ui/          components (design system), parenthome, payments, owner,
               employee, settings, reliability, diagnostics, onboarding, theme
supabase/
  migrations/  0001–0009 SQL
  functions/   fcm-gateway (Deno/TypeScript Edge Function)
docs/          audit, threat model, MASVS checklist, design system, analytics
```

---

## 10. Documentation assets that exist today

| File | What it is |
|---|---|
| `AGENTS.md` | Contribution rules for AI agents and humans: architecture, security rules, layout rules |
| `DESIGN.md` | Brand and design system source of truth (colours, type, shapes) |
| `docs/AUDIT_REPORT.md` | Security and performance audit |
| `docs/THREAT_MODEL.md` | Assets, trust boundaries, threats |
| `docs/MASVS_CHECKLIST.md` | MASVS-aligned checklist with per-control verdicts |
| `docs/DESIGN_SYSTEM.md` | Component library and its Gradle enforcement gates |
| `docs/ANALYTICS.md` | Closed catalogue of the analytics events the app may send |
| `docs/screenshots/*.png` | 3 real screenshots (home, payments, settings), recaptured from the current build on 2026-10-04 |
| `supabase/README.md` | Backend setup and deployment |

---

## 11. Release status — verify before adding any release badge

There **is** a published GitHub release. An earlier draft of this file claimed
otherwise; that was wrong and is corrected here.

**State as of 2026-10-04, re-verified against the API:** the `v2.9.0` *release*
no longer exists — `gh api repos/Vivekray898/PayVoice/releases` returns only
`v0.1.0-phase1`, and `gh release view v2.9.0` now fails. The `v2.9.0` **git tag
has since been deleted from the remote as well**: `git ls-remote --tags origin`
returns only `v0.1.0-phase1` (three consecutive reads), and both
`/releases/tag/v2.9.0` and `/tree/v2.9.0` return **404**. The `payvoice-v2.9.0.apk`
asset is gone with the release. Nothing about `v2.9.0` is linkable any more, so
no doc may link to it.

Note: an earlier revision of this section claimed the tag survived. It did when
first checked, and stopped being true during the same session — the removal
happened after that check, not before it. Re-verify before relying on it.

| Fact | Value | How checked |
|---|---|---|
| Current release | `v0.1.0-phase1`, **tag still at `9e028fa`**, asset `payvoice-0.1.0-phase1.apk` (3,185,297 B) built from `446d1d3` | `gh release view v0.1.0-phase1` |
| `v2.9.0` release | **Withdrawn.** No release page, no `payvoice-v2.9.0.apk` asset | `gh api repos/.../releases` |
| `v2.9.0` tag | **Deleted from the remote.** Local clones still hold the tag object | `git ls-remote --tags origin` (3 reads) |
| Tag's commit still on `main` | yes — `1d8f27e` "new added version realse ready" is an ancestor of `origin/main`, so no history is lost | `git merge-base --is-ancestor` |
| No Google Play listing | — | none referenced anywhere in the repo |

**Canonical version:** `0.1.0-phase1` / `versionCode 1` in `app/build.gradle.kts`,
adopted by maintainer decision on 2026-10-04. The `v2.9.0` number is retired.
A signed pre-release **is** published: `releases/tag/v0.1.0-phase1`, asset
`payvoice-0.1.0-phase1.apk` (3,185,297 B) built from `main` at `446d1d3` —
the dedupe fix. Version, `versionCode` and signing key are unchanged, so it
replaces the earlier assets in place and upgrades over them.

**Known drift, stated rather than hidden:** the `v0.1.0-phase1` **git tag
still points at `9e028fa`**, which no longer names the commit the attached APK
was built from. The tag was last moved forward to align it with the asset; this
upload re-introduces the same gap. Either the tag is moved to `446d1d3` or the
gap is accepted deliberately — but the docs must never claim the tag *does*
name the built commit, which is the error `ad08495` was written to correct. It is signed with the release certificate
(`CN=PayVoice`, SHA-1 `8ddbcbed1ffd5cc76d6f19cf5fce22da060a1d8f`), R8-minified
to a single dex, with no LeakCanary and no `READ_SMS`. Verified on an API 36
emulator: installs, completes onboarding, renders the four-tab Home screen.
The older `v2.9.0` release has since been withdrawn (release page and asset
deleted), and its tag was deleted from the remote afterwards. It is referenced
here by commit `1d8f27e`, which remains on `main`, never by tag or URL.

**Inconsistencies found, and what was done about them (2026-10-04):**

1. **`v2.9.0` is not built from `main` as it stands.** The tag predates the
   security audit, the Supabase/Firebase rework and the UI redesign. `minSdk` was
   `24` at the tag and is `26` now. The release page was retitled to carry the
   canonical version, with the artefact's real identity and its superseded
   status stated in the first section of the body.
2. **The release notes were inaccurate about the current project.** They
   advertised an "SMS fallback" and instructed users to grant SMS permission.
   That was true at the tag (`v2.9.0:app/src/main/AndroidManifest.xml` carries
   `READ_SMS`), but the current tree has **no** SMS permission, no `SmsManager`
   and no SMS parser. Current code reads Google Pay notifications only. The
   notes were rewritten; the false claims now appear only inside an explicit
   "Corrections" list that retracts them.
3. **Title/tag disagreement.** The release was titled "PayVoice v1.0.0" while
   the tag was `v2.9.0`, and the body's `Full Changelog` link pointed at
   `commits/v1.0.0`, a tag that does not exist — a dead link. Both fixed; the
   broken link was dropped rather than repointed.
4. **Other stale claims removed at the same time:** "end-to-end delivery
   confirmed, `fcmLatency=1710ms`" (the reliability doc records that leg as NOT
   VERIFIED), "release build is unminified" (`isShrinkResources = true` with
   `app/proguard-rules.pro`), and "unit test suite: 152/152 passing" (re-ran
   2026-10-04: **129 tests, 0 failures, 0 skipped**, 12 suites).

**Why there is still no release badge:** shields.io's
`github/v/release` endpoint ignores pre-releases and returned the literal text
`"no releases or repo not found"` for this repository. Adding it would have
shipped a visibly broken badge. The status is stated as text instead.

## 12. Remaining gaps

1. **No `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`,
   `CITATION.cff`, or `llms.txt`** — all to be created.
2. **Hindi README** — skipped by maintainer decision on 2026-10-04.
3. **Screenshots are the 3 in `docs/screenshots/`** — recaptured from the
   current four-tab build on 2026-10-04 (API 36 emulator, 1344x2992). There is
   deliberately **no** Team screenshot: that screen renders a live single-use
   pairing code behind `FLAG_SECURE` (`PvSecureWindow`), and Android refuses to
   capture a secure window. The old stale set (1080x2400, 40-86KB) and
   `03-team-owner.png` were deleted rather than kept alongside the new ones.
4. **No repository description, topics, or website URL set** (outside the files).
5. **Security finding, not a README fact:** a Firebase **service-account** key
   (`payment-announcer-43071-firebase-adminsdk-fbsvc-*.json`) sits in the
   working tree. It is untracked and gitignored, so it has never been committed,
   but `AGENTS.md` states FCM secrets belong in Supabase Edge Function Secrets
   only. Recommend deleting the local file once the deployed secrets are
   confirmed working.
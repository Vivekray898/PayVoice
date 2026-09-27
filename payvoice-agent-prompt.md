# PayVoice — Coding Agent Task Prompt

## ROLE
You are a senior Android engineer fixing a **production app**, not prototyping one. Inspect before you touch anything. Every change must be minimal, justified, and traceable to a specific bug or requirement below. If you cannot verify a claim on a real device/emulator, say so explicitly — do not report success without evidence.

## HARD CONSTRAINTS (violating any of these is a failed task)
- Do NOT rewrite the app, the parser registry, or the payment-detection architecture.
- Do NOT replace `NotificationListenerService` with a normal Service.
- Do NOT manually `startService()` the listener — Android owns its lifecycle.
- Do NOT add a foreground service, polling loop, or socket to "keep the listener alive."
- Do NOT let Room, Firebase, WorkManager, or TTS-init sit on the announcement critical path.
- Do NOT ever announce "unknown user" / "unknown sender" / "someone" — omit the sender instead.
- Do NOT merge sender identity across sources (GPay notification ≠ Kotak SMS, even for the same payment).
- Do NOT trust `DataStore`/persisted booleans as proof of Android system state — always re-query Android.
- Do NOT log full notification/SMS bodies, account numbers, or UPI refs in any build.
- Do NOT keep any Kotak **notification-app** listening/parsing/debugging code — GPay is the only notification-based UPI source now (see Phase 3).
- Do NOT let SMS reading depend, directly or indirectly, on whether the GPay notification listener is enabled — SMS must be captured through its own independent, always-registered path.
- Do NOT add a foreground service or continuous wake lock to solve reboot/background reliability — use `BOOT_COMPLETED`, the manifest-registered SMS receiver, and Android's normal background-execution rules instead.
- Do NOT let TTS/setup code assume any prior app state exists — first launch, post-reboot launch, and post-Clear-Data launch must all reach a fully working state through the same code path, not three different ones.

---

## PHASE 0 — INSPECT FIRST
Trace and document (internally, before editing) the real current flow for both paths:
```
Notification → NotificationListenerService → parser → PaymentModel → dedup → Room → AnnouncementManager → TtsManager
SMS → BroadcastReceiver → parser → PaymentModel → dedup → Room → AnnouncementManager → TtsManager
```
Identify what currently owns: `PaymentPipeline`, `ParserRegistry`, `PaymentRepository`, `AnnouncementManager`, `TtsManager`, setup-state/DataStore, `RetentionWorker`. Confirm whether the listener/TTS are Activity-scoped or app-scoped — this determines most of Phase 1's fixes.

---

## PHASE 1 — LIFECYCLE & SYSTEM-STATE TRUTH (Problem A)
1. **Ownership**: Move `PaymentPipeline`, `ParserRegistry`, `PaymentRepository`, `AnnouncementManager`, `TtsManager` into an app-level `AppContainer` (built in `PayVoiceApp`, not `MainActivity`/`MainViewModel`). Listener and SMS receiver pull dependencies from `AppContainer`, never from Activity/ViewModel/Compose state.
2. **Authoritative state check**: One helper function reads `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` and checks for the exact component `com.vivekray898.payvoice/com.vivekray898.payvoice.service.notification.PayVoiceNotificationListener`. This is the *only* source of truth for "notification access enabled" — never a DataStore flag.
3. **Reliability screen**: Enabled/Not Enabled is derived live from step 2, every time, not cached. "Enable Notification Access" button → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
4. **MainActivity.onResume()**: refresh all of — notification listener access, `POST_NOTIFICATIONS`, SMS permission, battery-optimization exemption, TTS availability, FCM registration state. No full-app restart required to reflect a Settings change.
5. **Clear Data must be a first-class state**, not an edge case: on launch, always query Android → refresh setup state → render Reliability screen. Never assume prior DataStore values are valid post-clear.
6. **Lifecycle logging** in `PayVoiceNotificationListener`, tag `PayVoiceNLS`, DEBUG only, package-name-only (no notification bodies):
   `onCreate`, `onListenerConnected`, `onListenerDisconnected`, `onNotificationPosted(package=…)`, `onNotificationRemoved`, `onDestroy`.
7. **onListenerDisconnected**: log it; if Android exposes a supported rebind-request API, use it; otherwise let Android manage reconnection. No retry loop, no polling.
8. **DeadObjectException root-cause**: use the lifecycle logs to determine actual sequence — check for: shared infra closed in `onDestroy()`, Activity-owned dependencies inside the listener, coroutine scopes tied to Activity, any `stopSelf()` call, process death vs. real crash. Fix the root cause; do not swallow the exception.
9. **DEBUG-only diagnostics panel** on Reliability screen: System enabled Y/N, Service connected Y/N, last `onListenerConnected` timestamp, last notification timestamp + package. If system-enabled=Y but connected=N, show that exact mismatch to the user.

**Must survive real testing**: fresh install → grant access → real GPay payment → `onListenerConnected` + `onNotificationPosted` in logs. Clear Data → relaunch → system state re-detected correctly. Swipe from Recents + lock screen → listener still captures + TTS still fires. Screen off → same.

---

## PHASE 2 — RUNTIME PIPELINE FIXES (Problem B)

### A. Source-aware payment model
`CaptureSource` is now (`GPAY_NOTIFICATION`, `SMS_KOTAK`, `SMS_BANK`). `KOTAK_NOTIFICATION` and any other non-GPay notification source are **removed** per Phase 3 — GPay notification is the sole primary/UPI-app source; SMS is the backup path when GPay doesn't produce (or the user has disabled) a notification. Source must still survive capture → parse → dedup → announcement unmodified.

### B. Kotak SMS parser (this is now the primary backup path — see Phase 3)
For real format:
```
Received Rs.1000.00 from Ms USHA DAS in your Kotak811 a/c XX8521 on 19-Sep-26. UPI ref no. 315101342750. View balance: https://kotk.in/KOTAKD/HSnXCv -Kotak
```
Extract sender strictly from the span between `from` and `in your Kotak811` (here: `Ms USHA DAS`). Never mistake `Kotak`, `Kotak811`, `XX8521`, `UPI`, the ref number, or the SMS sender ID for the sender name. Also parse: direction, `amountMinor` (1000.00 → 100000), bank, account, UPI ref. "debited" SMS must classify as NOT RECEIVED.

### C. Name normalization (deterministic, no identity inference)
`Ms USHA DAS` → `Ms Usha Das`, `MR RAHUL SHARMA` → `Mr Rahul Sharma`, preserve initials/punctuation (`S K SHARMA` → `S K Sharma`, `A. K. GUPTA` → `A. K. Gupta`). Whitespace cosmetic-only; keep honorifics.

### D. Announcement rules — absolute
- No sender available → `"Received {amount} rupees."` — never a placeholder person.
- Sender available **and trusted for that source** → `"Received {amount} rupees from {name}."`
- GPay/Kotak notification: only announce sender if the source itself provided a trustworthy one — never borrow it from a correlated SMS event.
- Implement the per-source fallback exactly:
```kotlin
when (payment.source) {
    SMS_KOTAK, SMS_BANK ->
        if (!payment.sender.isNullOrBlank()) announceAmountAndSender() else announceAmountOnly()
    GPAY_NOTIFICATION ->
        if (payment.senderIsTrusted && !payment.sender.isNullOrBlank()) announceAmountAndSender()
        else announceAmountOnly()
}
```
- Never expose account numbers, UPI refs, balance URLs, dates, or full SMS in the spoken announcement.

### E. Notification + SMS dedup for the same payment → exactly one announcement, spoken from whichever event's rules apply per source (don't fabricate a merged identity).

### F. Latency — measure, then fix
Add DEBUG-only timing tag `PaymentTiming`: `CAPTURED`, `PARSED`, `DEDUP_CHECKED`, `TTS_REQUESTED`, `TTS_STARTED`, plus a computed `capture→ttsStart` delta. Use it to find the actual bottleneck among: parsing, Room, dedup, Firebase, WorkManager, coroutine dispatch, TTS cold-start.
- Target critical path: `capture → parse → dedup → tts.speak()` — no Room write, no Firebase call, no WorkManager enqueue in between. Persist to Room *after* the speak call, in the background.
- `onNotificationPosted()` / SMS `onReceive()` must stay lightweight: extract minimal fields, hand off, return immediately. Use `goAsync()` in the SMS receiver only for short work, never long operations.
- TTS is app-level, initialized asynchronously at app start so it's warm before the first payment; never `new TextToSpeech()` per payment; never block on TTS init from the capture thread; if disconnected, reconnect async without blocking capture.
- `RetentionWorker`: use `enqueueUniquePeriodicWork`, verify it isn't re-enqueued/executed synchronously on every launch, and keep it out of the first-frame startup path.

### G. Startup performance
Given `Davey! duration=775ms / Skipped 89 frames!`, profile — don't guess. Check Compose init, DataStore reads, Room init, WorkManager, TTS, Firebase, notification setup, any synchronous disk/DB work, setup-state collection, nav init. Render UI immediately; query Android system state and warm TTS/Firebase/Room asynchronously afterward.

---

## PHASE 3 — THIS ITERATION'S CHANGES

### A. Rewrite TTS/voice readiness so it is instant on *every* cold start
Today TTS may come up late after reboot/Clear Data/first install. Fix the actual init path, don't paper over it with delays:
1. `TtsManager` initialization must be triggered from `PayVoiceApp.onCreate()` unconditionally — first install, post-reboot, and post-Clear-Data must all go through this **one** code path. Do not special-case any of the three.
2. Initialize the `TextToSpeech` engine asynchronously immediately at process start; do not gate it behind DataStore reads, Room init, Firebase init, or any setup-state check — those can all run in parallel.
3. Add an explicit "TTS ready" state (e.g. a `StateFlow<Boolean>` on `TtsManager`) that the pipeline can check; if a payment arrives before TTS reports ready, queue the single most recent announcement and speak it the instant `onInit(SUCCESS)` fires — do not drop it, and do not block the capture thread waiting for it.
4. Handle `TextToSpeech.ERROR` / engine-not-installed / language-data-missing explicitly: log it, retry init with backoff (a few attempts, not infinite), and degrade gracefully (skip speaking, keep capturing/persisting) rather than crashing or silently going dead for the rest of the process lifetime.
5. On **every process start** — this covers fresh install, reboot, and Clear Data identically, since all three are just "process starts with no/blank persisted state" — confirm via a real test that `TtsManager` reports ready and a test payment is spoken with no manual app open required first.

### B. Remove Kotak as a notification-based UPI app
Kotak's own app notifications are no longer a supported source. GPay is the only notification-based UPI source.
1. Delete the Kotak **notification** parser, its entry in `ParserRegistry`, any Kotak-app-specific package-name matching/detection, and any Kotak-notification-specific debug logging or diagnostics UI strings.
2. Do **not** touch the Kotak **SMS** parser (Phase 2B) — that stays and becomes the primary backup path (see below). Don't let the removal of Kotak-notification code accidentally break Kotak-SMS parsing; they must be fully decoupled in the codebase (separate files/classes, not shared conditionals).
3. Update the Reliability screen, diagnostics panel, and any user-facing copy that referenced "Kotak notification" to remove it; SMS backup status should be shown instead (see below).
4. Update `ParserRegistry` so GPay notification → SMS (Kotak/bank) is a real priority order: GPay notification is primary; SMS is the fallback used when GPay doesn't produce a usable event (listener disabled by user, GPay didn't post a notification, GPay's notification lacked parseable data, etc.).

### C. Fix SMS not being read when GPay notifications are disabled
This is a real bug, not a hypothetical — investigate and fix the root cause, don't just re-register something and hope:
1. Check whether the SMS `BroadcastReceiver` is registered dynamically and conditioned (even indirectly) on notification-listener state — e.g. registered inside a code path that only runs when the listener connects, or gated by a "GPay available" flag. It must be registered independently, ideally via the manifest (`<receiver>` with `SMS_RECEIVED` intent-filter) so it works regardless of listener/app/UI state.
2. Check whether SMS runtime permission (`RECEIVE_SMS`/`READ_SMS`) is actually granted at the moment the receiver fires, and whether the code silently no-ops when it isn't — surface this state on the Reliability screen instead of failing silently.
3. Check for any leftover logic that treats GPay-notification-enabled as a reason to *skip* SMS processing (e.g. an "if GPay handled it, ignore SMS" short-circuit that's misfiring when GPay is actually disabled, not when it successfully handled the payment). SMS must always be parsed and only get *deduplicated against* an already-announced GPay event — never unconditionally suppressed based on listener state.
4. Verify on real Android versions with SMS broadcast restrictions (Android 8+ implicit broadcast limits, Android 12+ background restrictions) that the app's SMS receiver is correctly declared to still receive `SMS_RECEIVED_ACTION`, which is exempt from the implicit-broadcast ban but still worth confirming per current targetSdk behavior.
5. Prove the fix with a real test: disable notification access entirely → send a real Kotak-format SMS → confirm capture, parse, dedup, and TTS announcement all still happen from SMS alone.

### D. Background operation without battery drain or being killed
1. Do not add a foreground service, wake locks, or a background polling loop to achieve this — `NotificationListenerService` and a manifest-registered `SMS_RECEIVED` receiver are both OS-triggered and don't need to "stay alive" on their own.
2. Add a `BOOT_COMPLETED` receiver (manifest-registered, minimal work only) so any app-level warm-up (TTS init, setup-state refresh) happens right after boot without requiring the user to open the app.
3. Request battery-optimization exemption the correct way: ask the user via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (or send them to the "Unrestricted" battery setting), explain briefly why, and reflect the real state on the Reliability screen from `PowerManager.isIgnoringBatteryOptimizations()` — never assume it's granted.
4. Keep all periodic/background work (`RetentionWorker`, any sync) on `WorkManager` with real constraints (e.g. `NetworkType.CONNECTED` where relevant) so Doze/App-Standby don't get a reason to kill the process; the OS-triggered listener/receiver paths are what keep detection working, not a kept-alive process.
5. Confirm no code path in the listener, SMS receiver, or `AppContainer` init can throw an uncaught exception — wrap parsing/announcement in defensive try/catch that logs and drops the single event rather than crashing the process (a crash *is* what gets the listener killed and not restarted promptly). This is a production-hardening requirement, not just a nice-to-have.

---

## TESTING (do not weaken tests to pass the build)
Run in order, fix failures without deleting/softening tests:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew installDebug
```
Required unit tests:
- Kotak SMS parser: exact fixture above → `source=SMS_KOTAK, direction=RECEIVED, amountMinor=100000, sender="Ms Usha Das", account="XX8521", upiReference="315101342750"`, announcement = `"Received 1000 rupees from Ms Usha Das."`
- Variations: `Rs.500.00 … MR RAHUL SHARMA …` → `"Received 500 rupees from Mr Rahul Sharma."`; `Rs.25.50 … Ms PRIYA …` → `"Received 25.50 rupees from Ms Priya."`; a "debited" SMS → classified NOT RECEIVED.
- Announcement composer: no-sender case never emits "unknown user"; source-specific fallback matrix (all 3 remaining `CaptureSource` values: `GPAY_NOTIFICATION`, `SMS_KOTAK`, `SMS_BANK`) is unit-tested.
- Dedup: GPay-notification + Kotak-SMS pair for the same payment collapses to exactly one emitted announcement.
- Confirm `ParserRegistry`/codebase no longer references a Kotak-notification parser, package matcher, or related enum value (a grep/compile check counts as evidence here, not just "I removed it").

**Testing pyramid** (apply, don't just claim): JUnit + MockK/Turnip for parser/pipeline/dedup unit tests; Robolectric or instrumented tests for the `NotificationListenerService`/SMS-receiver plumbing; Espresso/Compose UI tests for the Reliability screen states (Enabled/Not Enabled/diagnostics mismatch banner); a manual real-device pass for the scenarios in Phase 1 **and** the new Phase 3 scenarios below, since these cannot be fully simulated:
- **Reboot**: reboot the device with everything already configured → without opening the app, send a real GPay payment → confirm capture + TTS (proves `BOOT_COMPLETED` warm-up and instant-ready TTS actually work).
- **SMS independence**: disable notification access entirely → send a real Kotak-format SMS → confirm capture, parse, dedup, and TTS still fire from SMS alone.
- **First install, no prior state**: fresh install → grant permissions → first payment (GPay) → TTS must speak on the very first payment, not a later one.
- **Battery**: confirm `PowerManager.isIgnoringBatteryOptimizations()` reflects reality on the Reliability screen, and that no foreground service/wake lock/polling loop was introduced (a code-level check, not just a UX check).

---

## BEST PRACTICES TO FOLLOW (reference-quality, not just "idiomatic")
- **Architecture reference**: follow the patterns in Google's official **[Now in Android](https://github.com/android/nowinandroid)** sample repo for app-level DI/container structure, unidirectional data flow, and WorkManager usage — it's the canonical modern-Android reference architecture. Don't copy it wholesale; use it to sanity-check your `AppContainer`/repository/WorkManager decisions.
- **Android security**: check against **OWASP MASVS / MASTG** (Mobile Application Security Verification Standard) for anything touching SMS content, PII in logs, and notification data — in particular MASTG's logging and data-storage checklists. Also apply Android's own **[App security best practices](https://developer.android.com/privacy-and-security/security-tips)**: no sensitive data in logcat even in debug, scoped permissions, no unnecessary exported components.
- **Notification listener correctness**: cross-check your implementation against Android's own **[NotificationListenerService docs](https://developer.android.com/reference/android/service/notification/NotificationListenerService)** re: `requestRebind`, process death, and binder lifecycle — this is exactly where the `DeadObjectException` investigation should start.
- **WorkManager**: follow Google's **[WorkManager guidance](https://developer.android.com/topic/libraries/architecture/workmanager)** on unique periodic work and constraints to fix the `RetentionWorker` duplicate/immediate-execution issue.
- **UI/UX**: align the Reliability/Diagnostics screen with **Material Design 3** guidance for status/error states (clear enabled/disabled iconography, actionable CTA button, non-alarming but unambiguous "disabled" state); DEBUG diagnostics section should be visually distinct (e.g., a dev-only banner/section) so it's obviously not user-facing production UI.
- **CI/security hygiene**: add/confirm lint (`./gradlew lint`), Kotlin static analysis (detekt/ktlint), and a GitHub Actions workflow that runs unit tests + lint on every PR — mirror the CI setup style used in Now in Android's `.github/workflows`.
- **Secrets/logging discipline**: no API keys, tokens, or PII in commits or logs; DEBUG log tags limited to exactly `PayVoiceNLS`, `PaymentPipeline`, `Announcement`, `TtsManager`, `PaymentTiming`, `MessagingRepo`, each redacted per the rules above.

---

## FINAL REPORT FORMAT (fill in from real testing, not assumption)
```
Fresh install: PASS/FAIL
Notification Access: PASS/FAIL
Notification Access reflects Android system state: PASS/FAIL
Listener connected: PASS/FAIL
GPay notification captured: PASS/FAIL
Kotak SMS captured (backup path): PASS/FAIL
Kotak notification code fully removed: PASS/FAIL
GPay sender: AVAILABLE/UNAVAILABLE
Kotak SMS sender extraction: PASS/FAIL
Unknown-user fallback: REMOVED/NOT REMOVED
GPay capture → TTS: X ms
Kotak SMS → TTS: X ms
TTS initialization on first install: WARM/COLD, X ms to ready
TTS initialization after reboot: WARM/COLD, X ms to ready
TTS initialization after Clear Data: WARM/COLD, X ms to ready
SMS still captured with notification access disabled: PASS/FAIL
Listener after Clear Data: PASS/FAIL
Listener after reboot: PASS/FAIL
Listener after Recents removal: PASS/FAIL
SMS receiver after Recents removal: PASS/FAIL
SMS receiver after reboot: PASS/FAIL
Screen-off detection: PASS/FAIL
Notification + SMS deduplication: PASS/FAIL
Battery-optimization exemption reflects real state: PASS/FAIL
No foreground service / wake lock / polling introduced: CONFIRMED/NOT CONFIRMED
RetentionWorker startup behavior: FIXED/NOT FIXED
Startup time: X ms
Skipped frames: X
Unit tests: X/X
```
Do not report PASS for any real-device row without an actual log line as evidence (e.g., `PayVoiceNLS: onNotificationPosted package=com.google.android.apps.nbu.paisa.user`).

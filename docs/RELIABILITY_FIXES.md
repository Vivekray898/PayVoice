# PayVoice — Notification Detection & SMS Fallback Reliability Fixes

> **SUPERSEDED — historical record, do not read this as current behaviour.**
> This document describes a branch of work whose SMS fallback was subsequently
> **removed** in commit `535412c` (*"refactor: remove SMS feature — UPI apps
> only, drops RECEIVE_SMS permission"*), which also deleted
> `PayVoiceBootReceiver` in `4e7658f`. Consequently, in the current tree:
>
> - **there is no SMS capture at all** — no receiver, no `RECEIVE_SMS`
>   permission, no `SmsPaymentParserRegistry` / `SmsSenderHints`;
> - `CaptureSource.SMS_KOTAK` / `SMS_BANK` are enum values that are never
>   constructed;
> - `CrossChannelDedupTest` still exercises a cross-channel fingerprint case
>   that can no longer occur, and the "30 s cross-channel window" it
>   describes does not exist;
> - Google Pay is the **only** capture source, which is the single largest
>   known cause of missed payments (see `docs/PAYMENT_PIPELINE.md` hop C2).
>
> The nine commits listed below are real and are still in history; the fixes
> are not all still in the tree. For what the pipeline does *today*, and for
> the measured evidence, read `docs/PAYMENT_PIPELINE.md` and
> `docs/PAYMENT_PIPELINE_FINDINGS.md`.

Date: 2026-09-29 · Scope: app-side reliability only. No changes to the
payment-announcement architecture, Supabase schema/RLS, `fcm-gateway`, TTS
engine, or listener threading model. Every change is a separate commit on
`main` in the `fix(<area>): <symptom> — <description>` format.

## Confirmed root causes & fixes

### 1. GPay notifications missed

| Commit | Symptom | Root cause | Fix |
|---|---|---|---|
| `44e6e78` `fix(nls)` | Notification from an OEM/regional GPay package never processed | Whitelist matched only the exact canonical package | `KnownPackages.isGooglePayPackage()` — exact canonical, observed `.india` variant, and dot-suffix-of-canonical rule (dot boundary blocks impostor packages like `evil.com.google...paisa.user`); wired into `PaymentSource.fromPackage`, `PaymentParserRegistry.parserForPackage`, and the pipeline gate. Debug builds now log `PayVoiceNLS ignored package=…` for every non-whitelisted notification so future gaps are visible in logcat. Tests: `KnownPackagesTest` (5 cases). |
| `4ac577f`→`fix(nls)` (extras) | Payment with the amount only in `EXTRA_BIG_TEXT`/`EXTRA_SUB_TEXT` silently dropped | **Confirmed bug:** pipeline parsed only `title + EXTRA_TEXT`; bigText/subText were stored for diagnostics but never parsed | `NotificationTextResolver.candidates()` — parse `bigText → text → subText` (best first, bigText wins because it is the expanded form of the same content), first successful parse wins. A whitelisted capture that contains a currency marker but parses to nothing now writes a `MISSED-PAYMENT?` entry to Diagnostics (raw content stays in the local capture store, never logged). Tests: `NotificationTextResolverTest` (5 cases). |
| `4ac577f` `fix(nls)` | Stuck-listener repair invisible; Doze effects mysterious | `scheduleStartupRepair` ran silently; battery-exemption state never logged | Debug-gated logs at every startup-repair decision point (skipped / scheduled / fired+rebind-result / no-op) and a one-line startup log when battery optimization is active. No behavior change. |
| — (verified, no change) | Suspected platform-double-delivery suppression of real payments | — | `isPlatformDuplicate` keys on `(pkg, id, tag, postTime)`; GPay notification updates carry a new `postTime`, so genuine new payments always pass. The 1s window is correct as-is. Threading model untouched per constraint. |

### 2. SMS fallback not working

| Commit | Symptom | Root cause | Fix |
|---|---|---|---|
| `5beb0f0` `fix(sms)` | `"INR 500.00 spent"` (SBI style) never parsed — offline payment missed | **Confirmed bug:** `spent` missing from `SENT_SIGNALS`, so the message scored below the transaction threshold and was dropped | Added `spent` to the debit signal family. New `BankSmsFormatTest` (10 cases) covers all five required formats: HDFC/ICICI `Rs.500.00 debited from A/c XX1234`, SBI `INR 500.00 spent`, Kotak/Axis `Rs 500 debited`, GPay-linked `Sent Rs.500.00 from …`, and `A/c XX1234 debited by Rs.500.00` — plus the RECEIVED side of the same formats and OTP/balance non-payment guards. |
| `5cc7b5f` `fix(sms)` | Bank SMS from smaller banks ignored | Sender-hint table too thin | Expanded `SmsSenderHints` with major PSBs (PNB, BoB, UCO, IOB, CBI, BoI, BoM), private/foreign banks (Yes, IDFC, IndusInd, Federal, RBL, Citi, HSBC, SCBL, KVB, Karnataka), small-finance (Jana, AU, Equitas, Ujjivan), wallets/PSPs (PhonePe, NPCI). Debug builds log rejected non-transactional senders (`PayVoiceSms ignored sender=…`, gated by `SmsPaymentParserRegistry.debugLogging` set from the pipeline's `isDebugBuild`). Personal numbers stay ignored (privacy rule unchanged). |
| `8525631` `fix(sms)` | SMS fallback dead even though the manifest declared `RECEIVE_SMS` | **Confirmed bug:** the dangerous permission was never requested at runtime — only an App-Details deep link existed | Runtime `RECEIVE_SMS` request on the Reliability screen (primary button + App-settings fallback) and a new optional-but-recommended onboarding step 5 explaining the offline benefit. Copy explains local-only processing. |
| `fe74e29` `fix(dedup)` | Same payment announced twice when both GPay notification and bank SMS arrive | **Confirmed bug:** cross-channel fingerprints only collided with a shared UTR **and** matching sender names; a bank SMS without a payer name produced a different fingerprint than the GPay notification | Two new `ProcessedEventDao` queries (`recentSmsExists` / `recentNonSmsExists`, `remote` rows excluded — employee-side dedup must never suppress a local capture) + a 30-second cross-channel same-amount suppression window in the pipeline, with a `dedup: cross-channel suppressed` diagnostic and `lastDedupWasDuplicate` set. UTR/sender fingerprinting (Phase 12) is unchanged and still primary. No schema change — uses existing `sourcePackage`/`amountMinor`/`announcedAtMs` columns. |

### 3. Employee devices missing/delaying announcements

| Commit | Symptom | Root cause | Fix |
|---|---|---|---|
| `01c4822` `fix(fcm)` | Employee stops hearing payments after days without opening the app | Token refresh only ran when the UI opened; FCM-delivery cold starts never refresh; stale `devices` row means the gateway dials a dead token | Process-start freshness check: if `devices.token_refreshed_at` is missing or older than 7 days, force a token fetch and re-register the device row. Bounded, network-less starts fail softly. Debug logs state the decision. |
| `703ef7c` `fix(fcm)` | Killed/rebooted employee process, offline → every FCM event denied `pairing-unknown`, payment lost | Authorization cache was in-memory only; cold process + failed fetch = no snapshot to decide from | `PairedSnapshot` is now persisted to EncryptedSharedPreferences on every successful own-row read and used as the final authorization fallback: fresh cache → bounded fetch → stale memory → persisted snapshot. Each fallback decision is debug-logged with snapshot age. Fail-closed behavior is preserved when no snapshot has ever existed. |
| `c3473e9` `fix(fcm)` | Employee never saw Doze risk | Battery setup lived only in the owner's onboarding | Reliability section on the employee screen: battery-optimization status line + direct exemption action (existing tiered `fixBattery` path). |
| — (verified, no change) | Suspected `PaymentNotification.post()` failure silencing TTS | — | Already compliant in the current code: `post()`/`cancel()` are `runCatching`-wrapped and `post()` is called first, before authorize/dedup/TTS. Left untouched. |

## Logging rules honored

Debug builds only, at every decision point: package received/ignored
(`PayVoiceNLS`), repair-path decisions (`ListenerRuntime`), battery-exemption
missing (`PayVoiceApp`), SMS sender rejected (`PayVoiceSms`),
cross-channel suppression + missed-payment markers (Diagnostics store), auth
fallbacks (`EmployeeRepo`), token staleness (`PayVoiceApp`). Never logged:
amounts, sender names, SMS bodies, tokens, raw notification text.

## Verification performed

- `./gradlew :app:testDebugUnitTest` — full unit suite green after every
  change (parser, fingerprint/dedup, remote-layer, composer, direction
  suites + 20 new cases in `KnownPackagesTest`,
  `NotificationTextResolverTest`, `BankSmsFormatTest`).
- `./gradlew :app:assembleDebug` — build green; APK at
  `app/build/outputs/apk/debug/app-debug.apk`.
- Compile-level verification of the release variant via the unit suite
  (JVM tests) and `compileDebugKotlin` per change.

## Required on-device test matrix (manual — needs real payments/SMS)

Automated tests cover parsing/fingerprinting logic; the delivery behaviors
below need real devices. Per the task rules, any fix that does not survive
this matrix should be reverted rather than stacked.

1. Owner online, GPay payment → TTS on owner + FCM → TTS on employee.
2. Owner offline, bank SMS arrives → TTS on owner + FCM → TTS on employee
   (exercises fixes: `spent`, whitelist, permission grant, cross-channel).
3. Owner online, GPay notification AND SMS both arrive → exactly one
   announcement (exercises the 30s window).
4. Owner revoked the employee → employee receives nothing (backend ACTIVE-only
   fan-out unchanged; client deny reasons unchanged).
5. Employee online → announce. Employee offline → announced once when FCM
   next delivers (eventId dedup unchanged).
6. Kill both apps, receive payment → both announce on next delivery
   (exercises the persisted pairing snapshot + process-start token check).
7. Logcat spot-checks (debug builds): `PayVoiceNLS onNotificationPosted
   package=com.google.android.apps.nbu.paisa.user`, `PayVoiceNLS ignored
   package=…` for non-payment apps, `PayVoiceSms ignored sender=…`, 
   `startup-repair` lines, `auth-fallback:` lines.

## Known residual risks (documented, not fixed by design)

- **SMS lagging > 30s without a UTR** after a GPay notification for the same
  amount can still double-announce. Widening the window further starts
  merging genuinely separate same-amount payments (a real risk in a shop);
  the current 30s value balances both.
- **Bank notifications are intentionally out of scope** (architecture rule):
  a bank-app notification is never a payment source; SMS is the only
  non-GPay channel.
- **Authorization fallback vs revocation**: the persisted-snapshot fallback
  means an offline cold-start employee announces from last-known pairing.
  The backend's ACTIVE-only fan-out remains the primary enforcement, and the
  event is still dedup-guarded, but a revoked-while-offline device can
  announce one already-in-flight event — identical to the pre-existing
  stale-cache behavior, now just durable across process death.
- The sender whitelist remains hint-based (not a hard allowlist) on purpose:
  pinning exact sender IDs would regress the generic-bank parsing the
  existing test suite asserts.

## Out-of-scope items noticed (not changed, per constraints)

- `.idea/misc.xml` carries a pre-existing uncommitted edit that predates this
  work; left untouched and uncommitted.
- `fcm-gateway/index.ts`, migrations 0001–0005, and `MainActivity` untouched.

# PayVoice Reliability — Manual Device Test Matrix

Corrected for this codebase where the generic template diverged (see
"Corrections vs the generic template" at the bottom). Fill in on your two
real devices. Code state under test: post-deliverables commits on `main`.

---

## ⚠️ PARTIAL EMULATOR RUN — 2026-09-30 (NOT the physical matrix)

**Build:** debug @ `9cda32c` (includes the remote-delivery fix below) on
clean installs; emulator-5554 = owner, emulator-5556 = employee; paired
for real (`PAY-2AT8X7` claimed; owner UI: "1 connected"; employee:
"Connected"). POST_NOTIFICATIONS + RECEIVE_SMS granted, battery whitelisted,
listener bound — Reliability all-green on the owner.

**REAL BUG FOUND AND FIXED by this run** (`9cda32c`): every
`payment_events` insert failed RLS 403/42501 because the owner sent the raw
64-hex dedup fingerprint as the id while RLS (0001), the fcm-gateway, and
the employee validator all require `evt_`-prefixed ids — **remote delivery
had never worked from the app on any device.** Fix + contract tests:
`RemoteEventValidator.prefixEventId` (155/155 tests).

| Scenario | Result (emulator run) | Evidence |
|---|---|---|
| 1 — Owner online, GPay (simulated) | **PARTIAL** | Owner local announce PASS (`capture→ttsRequested=6–12ms`, `ttsStart=143–150ms`); remote insert now ACCEPTED (test-send returns Sent; pre-fix log: `insert payment_events failed: http=403 42501`). Employee receive **NOT VERIFIED** — blocked on the Supabase Database-Webhook → fcm-gateway leg (dashboard logs only). Employee had a live process, fresh FCM token registered, ACTIVE pairing. |
| 2 — Owner offline, SMS | SKIPPED | Emulator cannot receive a real bank SMS. |
| 3 — Dual-channel dedup | SKIPPED | Requires a real notification+SMS pair. (Dedup logic itself exercised incidentally: two identical sims 5min apart → second silently suppressed by fingerprint — correct.) |
| 4–8 | NOT RUN | All measure employee DELIVERY; running them while the gateway hop is unverified would yield meaningless results. (Accidental S6 datapoint: `adb install -r` puts the app in stopped-state and FCM is deferred until first launch — matching the documented force-stop semantics.) |

**Verdict for this run: FIX-FIRST → the insert-layer bug is FIXED; the
delivery chain is BLOCKED on the owner pasting from the Supabase dashboard:**
1. Database Webhooks: is a hook attached to `payment_events` INSERT?
2. Edge Function `fcm-gateway` logs for the test events at 15:54–16:12
   (expect `fanout_started` / `fcm_accepted`, or the failing stage).
3. `select user_id, is_active, length(fcm_token), token_refreshed_at from
   devices order by token_refreshed_at desc limit 5;` (employee row fresh?)

**Then re-run this matrix on the two physical phones** (release build must
be rebuilt to include `9cda32c`).

---

**Test date:** ____________
**Owner device:** ____________ (model, Android version)
**Employee device:** ____________ (model, Android version)
**App build:** ____________ (commit SHA)
**Tester:** ____________

## Pre-test setup

- [ ] Both devices online (owner needs internet for fan-out; SMS needs mobile signal)
- [ ] Both devices: `POST_NOTIFICATIONS` granted
- [ ] Both devices: battery optimization exemption granted
  (Reliability screen → Battery Optimization: "Unrestricted";
  employee: Connection screen → Reliability → "Battery optimization: off")
- [ ] Owner device: Notification listener access granted AND listener
  connected (Reliability screen shows both "Enabled in Android settings"
  and "Listener connected")
- [ ] Owner device: RECEIVE_SMS granted (Reliability screen → "SMS Backup
  (offline payments)": Enabled)
- [ ] Employee paired: owner's Employees list shows the device as `Connected`
- [ ] Supabase gateway log stream open (Dashboard → Edge Functions →
  fcm-gateway → Logs)
- [ ] logcat capture running on both devices (debug builds only — all
  diagnostic logs are debug-gated):

  ```bash
  adb -s <owner> logcat -c && adb -s <owner> logcat | \
    grep -E "PayVoiceNLS|PayVoiceSms|PayVoiceFCM|PayVoiceApp|ListenerRuntime|EmployeeRepo|RemoteDelivery" > owner.log &
  adb -s <employee> logcat -c && adb -s <employee> logcat | \
    grep -E "PayVoiceNLS|PayVoiceSms|PayVoiceFCM|PayVoiceApp|ListenerRuntime|EmployeeRepo|RemoteDelivery" > employee.log &
  ```

---

## Scenario 1 — Owner online, GPay payment

**Steps:**
1. Owner device online (Wi-Fi or mobile data)
2. Receive a real ₹1 GPay payment
3. Wait 10 seconds
4. Check logcat on both devices

**Expected:**
- Owner: TTS announces within ~2s of the notification.
  Logcat: `PayVoiceNLS onNotificationPosted package=com.google.android.apps.nbu.paisa.user`,
  then pipeline `announced=queued`.
- Employee: TTS announces within ~5s. Logcat:
  `RemoteDelivery event=… stage=fcm_received → authorized → dedup first-seen → tts_requested`.
- Supabase `payment_events` gets one row; gateway fan-out reaches the
  employee token.

**Result:** ☐ Pass ☐ Fail

**If fail — capture:** owner + employee logcat around the payment; gateway
log lines; `select * from payment_events order by created_at desc limit 1;`

**Notes:** ____________________________________________

---

## Scenario 2 — Owner offline, SMS arrives

**Steps:**
1. Airplane-mode the owner device, then re-enable mobile network *only if*
   your device supports SMS without data; otherwise use a second SIM/Wi-Fi
   calling. Goal: SMS reachable, data unreachable.
2. Receive ₹1 via bank transfer to the GPay-linked account (SMS fires; GPay
   notification cannot).
3. Wait 30 seconds.

**Expected:**
- Owner: `PayVoiceSmsReceiver` fires (add `PayVoiceSmsReceiver` to the grep),
  TTS announces within ~2s of the SMS. No GPay notification seen.
- Employee: **no announcement** (owner offline — nothing is fanned out; this
  is expected and honest).

**Result:** ☐ Pass ☐ Fail

**If fail — capture:** owner logcat filtered to `PayVoiceSms|parser`;
Diagnostics screen (look for `MISSED-PAYMENT?` entries — they mark known
whitelisted captures that failed to parse); the raw sender ID + a redacted
body shape of the SMS.

**Notes:** ____________________________________________

---

## Scenario 3 — Dual-channel dedup (owner online, notification + SMS)

**Steps:**
1. Owner online.
2. Make a payment that triggers both a GPay notification and the bank SMS
   (most real UPI payments do).
3. Wait 15 seconds; check both devices and the Diagnostics screen.

**Expected:**
- Exactly ONE announcement on the owner; exactly ONE on the employee.
- Diagnostics shows a `CROSS_CHANNEL_SUPPRESSED` entry naming the losing
  channel, the rupee amount (integer), and the 10s window — the losing
  channel's announcement must be the suppressed one.
- One `payment_events` row.

**Result:** ☐ Pass ☐ Fail

**If fail — capture:** which device double-announced; timestamps of both
announcements; Diagnostics rows; whether the losing channel was SMS
(lag > 10s → widen `CROSS_CHANNEL_WINDOW_MS` in `PaymentPipeline`) or a
parser mismatch.

**Notes:** ____________________________________________

---

## Scenario 4 — Owner revokes employee

**Steps:**
1. Owner online; employee paired.
2. Owner: remove the employee (confirmation dialog → Remove).
3. Make a ₹1 GPay payment. Wait 15 seconds.

**Expected:**
- Owner: still announces locally.
- Employee: does NOT announce.
- `employees` row: `status = 'REVOKED'` (the employee `id` keeps its token —
  revocation is status-based, not token-blanking; see corrections).
- Gateway log: fan-out matched no ACTIVE employee for this event.
- Owner UI lists the employee as removed/revoked.

**Result:** ☐ Pass ☐ Fail

**Cleanup:** re-pair the employee.

**Notes:** ____________________________________________

---

## Scenario 5 — Employee app swiped away, payment arrives

**Steps:**
1. Owner online; employee paired. Swipe PayVoice away from Recents on the
   employee device (do NOT force-stop).
2. Make a ₹1 GPay payment. Wait 10 seconds.

**Expected:**
- Employee announces (FCM high-priority wakes the process).
- The silent "Payment announced" receipt flashes briefly
  (PaymentNotification, min-importance channel).
- Logcat: `RemoteDelivery … stage=fcm_received` — the process-start path
  also runs the stale-token check (`PayVoiceApp token freshness …`) and the
  pairing authorization (cache hit: `authorized` with no `auth-fallback`
  line; offline fallback would show
  `auth-fallback: kind=stale-memory|persisted-disk`).

**Result:** ☐ Pass ☐ Fail

**Notes:** ____________________________________________

---

## Scenario 6 — Employee force-stopped, payment arrives

**Steps:**
1. Employee device: Settings → Apps → PayVoice → **Force stop**.
2. Make a ₹1 GPay payment. Wait 15 seconds.

**Expected:**
- Employee does NOT announce (Android blocks FCM delivery to force-stopped
  apps — expected Android behavior, not a bug).
- Opening PayVoice once re-enables delivery.
- Gateway log: delivery to that token fails/unreachable.

**Result:** ☐ Pass ☐ Fail (documents expected behavior)

**Notes:** ____________________________________________

---

## Scenario 7 — Both apps killed, then delivery

**Steps:**
1. Swipe PayVoice away on BOTH devices.
2. Make a ₹1 GPay payment (swiping away the launcher task does NOT unbind
   the notification listener service — verify: owner logcat still shows
   `PayVoiceNLS onNotificationPosted`).
3. Wait 15 seconds.

**Expected:**
- Owner: announces (listener still bound; if not, the startup-repair logs
  `startup-repair FIRED … rebind=true` within ~8s of the next process start).
- Employee: announces on FCM delivery.
- If either stays silent: re-open the app on that device and check whether
  the payment appears in history (retroactive processing).

**Result:** ☐ Pass ☐ Fail

**Notes:** ____________________________________________

---

## Scenario 8 — Cross-device latency (informational)

**Steps:** 3 runs of Scenario 1; read `stage=tts_requested` timestamps from
both devices' logcat (`RemoteDelivery … fcm→ttsRequest=…ms`).

| Run | Owner TTS (ms after payment) | Employee TTS (ms after payment) | Delta |
|-----|------------------------------|--------------------------------|-------|
| 1   |                              |                                |       |
| 2   |                              |                                |       |
| 3   |                              |                                |       |

**Target:** employee within ~2s of owner on a good network. Consistently
>5s suggests FCM priority downgrade — confirm `PaymentNotification.post()`
runs on every message (it is the first action in `onMessageReceived`) and
give the 7-day priority-rebuild window before considering escalation.

**Notes:** ____________________________________________

---

## Post-test summary

| Scenario | Result | Notes |
|----------|--------|-------|
| 1 — Owner online, GPay | ☐ Pass ☐ Fail | |
| 2 — Owner offline, SMS | ☐ Pass ☐ Fail | |
| 3 — Dual-channel dedup | ☐ Pass ☐ Fail | |
| 4 — Revoked employee | ☐ Pass ☐ Fail | |
| 5 — Employee swiped away | ☐ Pass ☐ Fail | |
| 6 — Employee force-stopped | ☐ Pass ☐ Fail | expected-behavior doc |
| 7 — Both apps killed | ☐ Pass ☐ Fail | |
| 8 — Latency delta | median ____ ms | |

**Overall verdict:** ☐ Ready to ship ☐ Fix failures first ☐ Needs another iteration

**Sign-off:** _______________________ **Date:** _______________

---

## What to do with the results

- **All pass** → real-device evidence obtained; audit residual risks closed; ship.
- **Scenario 2 fails (SMS not announced)** → parser gap for your bank's
  format. Grab the redacted sender+body shape, add a `BankSmsFormatTest`
  case, fix, re-run. Check Diagnostics for `MISSED-PAYMENT?`.
- **Scenario 3 fails (double announcement)** → if the lag was >10s, widen
  `CROSS_CHANNEL_WINDOW_MS` (10s → 15s) and re-test; if fingerprints
  differed AND lag <10s, capture the Diagnostics `dedup` rows and file the
  wording pair.
- **Scenario 5 fails** → check `devices` row freshness (token_refreshed_at)
  and the gateway log; the 7-day stale-token refresh at process start may
  not have run yet on that install.
- **Scenario 8 >5s consistently** → priority-downgrade suspicion; verify the
  silent receipt notification actually posts (POST_NOTIFICATIONS grant),
  wait the 7-day window before any foreground-service escalation.

## Corrections vs the generic template

Deviations from the requested template, with reasons — do not "fix" these
back without reading the code:

1. **Scenario 2 expectation fixed:** the template expected an employee
   announcement while the owner is offline. Impossible by design — the
   owner's device cannot insert `payment_events` without connectivity, and
   the fcm-gateway only fires on inserts. "No employee announcement" is the
   correct expected result.
2. **Scenario 4 `fcm_token = ''` removed:** revocation in this codebase is
   status-based (`status='REVOKED'`; the gateway queries ACTIVE-only). No
   token is blanked, and blanking it would break re-pairing.
3. **`CROSS_CHANNEL_SUPPRESSED` diagnostic shape:** the pipeline writes
   `{tag: "CROSS_CHANNEL_SUPPRESSED", message: "<capture> for <rupees> —
   opposite channel seen within 10s"}` via the existing `diag()` store
   (Diagnostics screen, capped 300 rows) — not a free-form table insert.
4. **logcat grep updated:** the real tags are `PayVoiceNLS`, `PayVoiceSms`,
   `PayVoiceFCM`, `PayVoiceApp`, `ListenerRuntime`, `EmployeeRepo`,
   `RemoteDelivery` (the template's `DebugLog`/`SmsParser`/`PaymentNotification`
   tags do not exist as log tags; `PaymentNotification` posts notifications,
   not log lines).
5. **Scenario 6 verdict note:** a force-stopped employee not announcing is
   PASS (documented Android behavior), recorded as such in the table.
6. **Pre-test setup additions:** notification-listener connected state and
   RECEIVE_SMS grant are prerequisites specific to this app's two-channel
   design; without them scenarios 1–3 measure setup gaps, not reliability.

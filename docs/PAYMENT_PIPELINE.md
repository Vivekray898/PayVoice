# Payment pipeline — hop by hop

Every hop from "money received" to "voice spoken", with the file, what can
fail, and what the code does on failure **today**. This is a description of the
code as it is written, not a wish list: nothing here is fixed by this document.

Line numbers refer to the commit this file was written against; re-derive them
with `git grep -n` if a hop moved.

## 0. The two legs

PayVoice has two independent capture→speak pipelines that share the parser, the
dedup store and the speaker:

```
OWNER phone (detects)                    EMPLOYEE phone (hears)
─────────────────────                    ────────────────────────
NLS onNotificationPosted                 FCM onMessageReceived
  → PaymentPipeline.handleNotification     → PayVoiceMessagingService
  → parse → gates → dedup → speak          → authorize → dedup → speak
  → UPLOAD (fire-and-forget)                (no upload leg)
    → Supabase payment_events insert
    → Database Webhook → fcm-gateway
    → FCM HTTP v1
```

The owner announces locally and uploads afterwards. The employee never captures;
it only ever announces what arrives over FCM. **A payment that dies on the
upload leg is lost forever for the employee** — see hop U3.

---

## Hop C — Capture (owner only)

| | |
|---|---|
| **File** | `service/notification/PayVoiceNotificationListener.kt` |
| **Entry** | `onNotificationPosted(sbn)` → `dispatchIfWhitelisted(sbn)` (`:86`, `:104`) |
| **Alternative source** | None. There is no SMS receiver and no boot receiver. `CaptureSource.SMS_KOTAK` / `SMS_BANK` exist as enum values in `core/model/Models.kt:69` and are never constructed. |

### C1 — Platform double-delivery filter
`isPlatformDuplicate(sbn)` (`:183`) drops a second `(pkg, id, tag, postTime)`
tuple seen within 1 s.

*Can fail:* a genuine second payment that reuses the same notification id,
tag and postTime — impossible in practice, postTime is millisecond precision.
*On failure today:* **dropped silently**, one line in debug logcat
(`platform duplicate delivery dropped`, `:115`). No user-visible signal.

### C2 — Package whitelist
`PaymentSource.fromPackage(pkg)` (`:120`, `core/model/Models.kt:54`). The enum
has exactly **one** entry, `GOOGLE_PAY`; the fallback is
`KnownPackages.isGooglePayPackage`, which accepts the canonical package, one
named variant, and any dot-suffix of the canonical id (`Models.kt:27-35`).

*Can fail:* **any payment that does not arrive as a Google Pay notification is
dropped here.** A customer paid via PhonePe, Paytm, BHIM UPI, a bank app or an
SMS and PayVoice never sees it — there is no code path that could.
*On failure today:* **silent drop.** In debug builds only, `log("ignored
package=…")` (`:132`); in release, nothing at all.

### C3 — GPay capture toggle
`if (!settings.gpayEnabled) return` (`:150`).

*Can fail:* user toggles capture off in Settings, or never completes onboarding.
*On failure today:* **silent drop.** No trace row, no diagnostic row.

### C4 — Connect-time snapshot
`onListenerConnected` re-dispatches `activeNotifications` (`:72-73`).

*Can fail:* `activeNotifications` throws → `runCatching{}.getOrNull() ?: return`,
the whole snapshot is skipped and **notifications posted while the listener was
unbound are never recovered.**
*On failure today:* **silent.** No rebind is attempted and no retry happens.

---

## Hop P — Parse

| | |
|---|---|
| **File** | `PaymentPipeline.kt:136-182`, `core/parser/*` |
| **Functions** | `PaymentParserRegistry.parserForPackage` → `GooglePayParser.parse` → `DirectionClassifier.classify` → `AmountExtractor.extract` |

### P1 — Text candidate selection
`NotificationTextResolver.candidates(title, text, bigText, subText)`
(`core/parser/NotificationTextResolver.kt:19`) tries **bigText, text, subText**
in that order and takes the first candidate that parses.

*Can fail:* `EXTRA_TEXT_LINES` and `EXTRA_MESSAGES` are **never read**
(`PayVoiceNotificationListener.kt:122-126` reads only TITLE, TEXT, BIG_TEXT,
SUB_TEXT). A grouped/collapsed GPay notification that puts the payment line in
`EXTRA_TEXT_LINES` has no candidate containing it.
*On failure today:* **silent drop** at P2.

### P2 — Parser returns null
Two distinct causes, both a `return` at `PaymentPipeline.kt:181`:
- `NON_PAYMENT` keyword reject (`GooglePayParser.kt:29, :99-102`) — a list that
  contains `"failed"` and `"request"` as bare substrings, so a credit line that
  also says "failed" or "request" anywhere is discarded.
- `direction == UNKNOWN && amount == null` (`GooglePayParser.kt:34`).

*On failure today:* the raw notification is still written to
`captured_notifications` (`:151`), and a `diagnostic_log` row tagged `parser`
is written with either `MISSED-PAYMENT?` (when a currency marker is present) or
`no payment in capture` (`:176-180`). **The payment is never announced and
never stored as a payment.** Release builds write to the DB but the screen that
reads `diagnostic_log` is behind a debug-only navigation path only in practice.

### P3 — Direction / confidence / amount gates
`:187` (direction ≠ RECEIVED), `:191` (confidence below threshold), `:196`
(amount null or ≤ 0).

*Can fail:* a correct payment whose wording the weighted classifier resolves to
`UNKNOWN`, or resolves to `MEDIUM` while `announceHighConfidenceOnly` is on.
*On failure today:* **silent drop** with a `gate` diagnostic row. No amount, no
announcement, no event id.

---

## Hop D — Dedupe

| | |
|---|---|
| **File** | `core/parser/Fingerprinter.kt:60-93`, claimed at `PaymentPipeline.kt:204-226` |

### D1 — Fingerprint derivation
Two branches:
- **With a reference** (UTR / UPI ref / txn id, `Fingerprinter.kt:71-80`):
  `SHA-256("REF|<ref>|<amountMinor>")`. Correct — the same reference always
  collapses.
- **Without a reference** (`:81-91`):
  `SHA-256("AMT|<amountMinor>|<senderName.lowercase>|<postTime / 5min>")`.
  Google Pay credit notifications routinely carry **no** UTR, so this is the
  common branch for real payments.

*Can fail:* two genuinely distinct payments of the **same amount from the same
sender inside the same 5-minute bucket** produce byte-identical fingerprints.
*On failure today:* `INSERT OR IGNORE` returns `-1` (`:222`) → **`return` at
`:226`. The second payment is never announced, never stored, never uploaded,
and there is no retry.** One `dedup` diagnostic row says only "duplicate
suppressed" — it does not distinguish a true duplicate from a collision.

### D2 — Fingerprint doubles as the event id
`eventId = fingerprint` (`:216`) and the same value is used for the backend row
(`:280`).

*Consequence:* the same payment re-captured in a **later 5-minute bucket**
becomes a **second backend row and a second FCM message**. The employee dedupes
it (`PayVoiceMessagingService.kt:114`) so it is not double-spoken, but the
backend now holds two rows for one payment and `payment_events.id` no longer
means "one payment".

### D3 — Claim happens *before* the utterance
The dedup row is inserted at `:213`, the TTS is requested at `:274`, and the
history row is written at `:306` with `announcedAtMs = announcedAt` — the
`ttsRequestedAt` timestamp, taken at `:241` **before** `speakWhenReady` (`:274`)
is even called.

*Can fail:* process death, OEM kill, or a silent TTS engine between `:213` and
the actual audio. The fingerprint is consumed, `announcement_history` says the
payment was announced at a time when no audio existed, and **nothing anywhere
records it as un-announced, so nothing retries it.**

---

## Hop S — Speak (local)

| | |
|---|---|
| **File** | `service/tts/AnnouncementSpeaker.kt` |

### S1 — Wake-up notification
`PaymentAnnouncementNotifier.post(...)` (`PaymentPipeline.kt:256`), cancelled 5 s
later (`:268-273`).

*Purpose:* forces Android out of Doze / screen-lock so the engine can run.
*On failure today:* `posted == false` (e.g. `POST_NOTIFICATIONS` denied on API
33+) is **logged only in debug** (`android.util.Log.d`, `:266`) and the pipeline
continues anyway. The engine may then be deferred until the user unlocks.

### S2 — Engine warm/cold
`speakWhenReady(text)` (`:191`):
- engine ready → `speak(text)` immediately;
- engine **not** ready → `pendingSlot.set(text)` (`:197`) — **a single slot**,
  `AtomicReference<String?>` at `:67`. A second payment arriving before the
  engine is ready **overwrites the first**. `speakWhenReady` returns `true`
  nothing about it; the overwritten text is gone with no record.

The engine is released `IDLE_RELEASE_MS = 5 * 60_000` after every use
(`:341`, `:331`), so **it is cold for most real payments.** The
`onListenerConnected` warm-up (`:68`) expires after five minutes, and
`PayVoiceApp` does not keep it alive.

### S3 — Concurrent arrival during an utterance
`speak()` holds `speakMutex` (`:62`, `:80`). A payment that arrives during an
utterance takes the S2 branch and is parked in the single slot. When the engine
later releases, `flushPending()` (`:229`, called from `ensureEngine` `:295`)
speaks **only the last** parked text.

### S4 — `QUEUE_FLUSH`
`current.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)` (`:122`)
and the identical retry at `:163`.

*Can fail:* if a second `speak()` ever reaches the engine while a first
utterance is in flight, `QUEUE_FLUSH` **cuts off the in-flight announcement
mid-word**. The interrupted utterance then completes as a failure, the failure
path (`:138-174`) shuts the engine down, re-initialises it and speaks the
**same** text again — so the interrupted payment is heard twice, partially and
then in full.

### S5 — TTS failure path
`:138`: any non-success restarts the engine once and retries; a second failure
posts `TtsFallbackNotifier` — a **notification**, not audio — and returns
`false`. `speakWhenReady` never observes the return value, so a fallback
notification is the end of the road and **no trace records that audio never
happened.**

### S6 — Audio focus / volume
`requestFocus()` (`:308`) with `USAGE_ASSISTANCE_ACCESSIBILITY` +
`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. Denial is logged (`android.util.Log.w`,
`:321`) and the utterance is attempted anyway. **Zero media volume with focus
granted produces a silent, "successful" announcement** — `onDone` fires, the
payment is recorded as spoken, and no audio was produced.

---

## Hop U — Upload (owner → backend)

| | |
|---|---|
| **File** | `core/remote/RemoteEventSender.kt:44-89`, called at `PaymentPipeline.kt:278-289` |

### U1 — Role gate
Only when `roleProvider() == OWNER && remoteEnabledProvider()` (`:278`).
An employee never uploads. *On failure today:* n/a.

### U2 — Auth
`auth.ensureSignedIn() ?: return` (`RemoteEventSender.kt:55-58`).

*Can fail:* not signed in, token refresh in flight, Supabase unreachable.
*On failure today:* `_lastSendState = FAILED("no-auth")` and **`return@launch`.
No queue, no retry, no outbox row. The payment never reaches the backend.**

### U3 — Insert
`client.insertRow("payment_events", …, onConflictMerge = true)` (`:70`).

*Can fail:* airplane mode, offline, DNS failure, 5xx, timeout, 401.
*On failure today:* `_lastSendState = FAILED("send-failed")` (`:85`) and a debug
log line. **There is no outbox table and no WorkManager job for this** — the
only scheduled work in the app is the daily retention prune
(`PayVoiceDatabase.kt:41`, `RetentionCleaner`). Nothing ever retries. A payment
the owner captured while offline is a **permanent employee loss**, and the
owner's Diagnostics screen shows only the *last* send state, so the loss leaves
no per-payment record.

---

## Hop B — Backend / Edge Function

| | |
|---|---|
| **Files** | `supabase/migrations/*.sql` (`payment_events`, RLS, AFTER INSERT trigger), `supabase/functions/fcm-gateway/index.ts` |

### B1 — Row insert
`payment_events.id` is the `evt_…` id, unique → idempotent. RLS restricts
insert to the authenticated owner.

### B2 — Database Webhook → `fcm-gateway`
The AFTER INSERT trigger invokes the function with the row under `record`.
`parseEvent` rejects anything that is not `evt_`-prefixed, ≤64 chars, type
`PAYMENT_RECEIVED`, integer `amount_minor` in range, `currency === "INR"`.
Anything else returns `null` and the fan-out **silently does not happen**.

### B3 — Fan-out
Employee ids → device tokens → FCM **HTTP v1** `messages:send`
(`fcm_accepted` / `fcm_send_failed` / `fanout_finished` log stages, `:352`,
`:371-382`, `:408`). The `fcm-gateway` log line carries `event=<id12>`, i.e.
the first 12 characters of the event id — **this is the only `FCM_SENT`
evidence available anywhere**, and it is server-side.

---

## Hop F — FCM receive (employee only)

| | |
|---|---|
| **File** | `service/messaging/PayVoiceMessagingService.kt:48-213` |

### F1 — Payload validation
`RemoteEventValidator.validate(...) ?: return` (`:52`).

*On failure today:* `stageLog(stage="dropped", reason="invalid payload")` in
**debug builds only**. Release: silent.

### F2 — Authorisation
`employees.authorizeRemoteEvent(ownerUid)` (`:88`), fail-closed.

*Can fail:* the `employees` row is not `ACTIVE`, the pair was revoked, the read
times out.
*On failure today:* `stageLog(stage="denied")` (debug only) and `return@launch`.
**The employee never learns a payment was withheld.**

### F3 — Remote dedupe
`processedEventDao().insert(fingerprint = event.eventId)` (`:114`).
`claimed == -1L` → `stageLog(stage="dedup", reason="duplicate")` (`:124`),
debug only, and `return@launch`.

### F4 — GoAsync
`acquirePendingResult()` (`:78`, impl `:230`) resolves `goAsync` reflectively.
If the method is missing or the invocation throws it degrades to a **no-op** and
the process becomes killable while the work above is still in flight.

### F5 — Announce
`composeSms` → `PaymentAnnouncementNotifier.post` (`:147`) →
`speaker.speakWhenReady(text)` (`:152`) — the **same single-slot parking** as S2.

*On failure today:* identical to the owner leg. The history row written at `:156`
records `announcedAtMs = ttsRequestedAt` — a timestamp taken *before* any audio.

---

## Summary: every place a payment can die silently

| # | Hop | Mechanism | Silent? | Retried? |
|---|---|---|---|---|
| 1 | C2 | Only Google Pay is ever a capture source | yes | no |
| 2 | C4 | `activeNotifications` throw skips the whole snapshot | yes | no |
| 3 | P1 | `EXTRA_TEXT_LINES` / `EXTRA_MESSAGES` never read | yes | no |
| 4 | P2 | `NON_PAYMENT` substring reject ("failed", "request") | db row only | no |
| 5 | P3 | Direction / confidence / amount gate | db row only | no |
| 6 | D1 | Amount+sender+5-min-bucket fingerprint collides | db row only | no |
| 7 | S2/S3 | Single-slot parking, latest wins, engine cold 5 min | yes | no |
| 8 | S4 | `QUEUE_FLUSH` truncates + retry re-speaks | yes | re-speaks |
| 9 | S6 | Zero volume = "successful" silent utterance | yes | no |
| 10 | U2/U3 | No outbox, no retry after auth/insert failure | debug log only | **no** |
| 11 | D3 | Dedup claimed before speech; no un-announced state | yes | no |
| 12 | F1/F2 | Invalid payload / denied event | debug log only | no |

Cross-reference: `docs/PAYMENT_PIPELINE_FINDINGS.md` is the measured version of
this table — which of these actually fire, and how often, from the Step 3
replay harness and from real-payment traces.
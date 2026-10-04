# Payment pipeline — replay findings (Step 3)

Companion to [PAYMENT_PIPELINE.md](PAYMENT_PIPELINE.md) (the hop-by-hop map).
Everything below is measured output from the instrumentation replay harness,
`app/src/androidTest/.../replay/PaymentReplayHarness.kt`, driving the **real**
`PaymentPipeline.handleCapture()` with 66 synthetic fixtures plus targeted
scenarios.

## 0. Provenance — read this before quoting anything below

| | |
|---|---|
| Harness run | `tests="11" failures="0" errors="0"` in 623 s (`app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_8_Pro(AVD) - 14.xml`) |
| Device | **Emulator only** — Pixel 8 Pro AVD, API 34, Google TTS present |
| Real device | **No data.** The owner's phone (Redmi Note 10 Pro, `sweetin`) was not connected for this run. |
| Capture hop | **Not exercised.** The harness injects a `CaptureEvent`; `PayVoiceNotificationListener` never sees a real notification. Nothing here says anything about whether a real bank/GPay notification reaches the pipeline. |
| Network hops | **Not exercised.** `UPLOADED` / `FCM_SENT` / `FCM_RECEIVED` are absent from every chain below; the run is capture-side only. |

**What is proven:** the local dedupe key merges two *distinct* payments that share
an amount and a sender inside one 5-minute bucket (§1), and it does so
nondeterministically depending on where the wall clock happens to sit (§2).

**What is NOT proven:** that this is the cause of "8 of 10 real payments" on the
owner's phone. That claim needs a real-device capture trace of the two missing
payments, which does not exist yet. Treat the defect as confirmed and its blast
radius on that phone as unmeasured.

## 1. Confirmed defect — `dedup-amount-bucket-collision`

### The key

`Fingerprinter.captureFingerprint()` (`core/parser/Fingerprinter.kt:60-93`):

```
reference present : "evt_" + sha256("REF|" + REFERENCE.trim().uppercase() + "|" + amountMinor).take(60)
reference absent  : "evt_" + sha256("AMT|" + amountMinor + "|" + sender.trim().lowercase() + "|" + timestampMs / 300000).take(60)
```

`timestampMs` is `event.postedAtMs` — the notification's own post time, bucketed
against the **wall clock** (`CROSS_CHANNEL_BUCKET_MS = 5 * 60_000`). The row is
inserted with `INSERT OR IGNORE` into `processed_events`, which carries
`UNIQUE(fingerprint)`; a collision returns `-1` and the pipeline drops the
payment with reason `dedup-amount-bucket-collision`, `ref=0`.

### Which realistic cases are wrongly dropped

| Case | Same amount? | Same sender? | Reference? | Result | Why |
|---|---|---|---|---|---|
| Two ₹500 payments from Rahul Sharma, 59 s apart | yes | yes | no | **2nd DROPPED** | same amount + sender + wall-clock bucket |
| Three ₹500 payments from Rahul Sharma in 5 min | yes | yes | no | **2nd + 3rd DROPPED** | measured, §1.1 |
| A payment re-posted by the OS (identical repost) | yes | yes | no | dropped (correct) | genuinely the same source event |
| Same amount, **different** sender (₹500 Rahul → ₹500 Amit) | yes | no | no | both announced | sender is in the key |
| **Different** amount, same sender (₹500 then ₹700 Rahul) | no | yes | no | both announced | amount is in the key |
| Any payment carrying `UPI Ref` / `Txn ID` | — | — | yes | announced | `REF|` key ignores the bucket |
| Payments ≥ 5 min apart that land in different buckets | — | — | no | both announced | only by luck of the wall clock (§2) |

**The failing class is exactly the ordinary one**: repeated payments of the same
round amount to the same person — the ₹500 transfer, the split bill, the second
chai — and only when the sender's app does not attach a UPI/Txn reference.

### 1.1 Measured

`fingerprint-bucket-boundary` — five identical notifications ("₹500 received from
Rahul Sharma"), replayed at t+0 s, +240 s, +299 s, +301 s, +320 s from one base
timestamp. Expected: 5 announcements. Observed: **2 announced, 3 dropped**.

```
t+0s=SPOKEN  t+240s=SPOKEN  t+299s=DEDUPED  t+301s=DEDUPED  t+320s=DEDUPED
```

`t+299s` is **59 seconds** after an announced payment of the same amount from the
same sender and it never reached the speaker. `t+240s` announced only because
that offset happened to cross the 5-minute boundary.

`threeSameAmountPaymentsWithinFiveMinutes` — `r01` `QUEUED`, `r02` `DEDUPED`,
`r03` `DEDUPED`. Two of three genuine payments lost (reproduced identically in
three separate runs).

Corpus group `₹500 from Rahul Sharma` — `c01`, `x14`, `x15`, `r01`, `r02`, `r03`
plus the ambiguous `a05`, `a07`, `a08`, `a10` (ten reference-less events that all
parse to the same amount + sender): **2 announced, 8 dropped**. Group `₹700 from
Vivek` (`x06`, `x07`): 1 announced, 1 dropped.

## 2. Confirmed — the victim set is wall-clock dependent

The same corpus was swept twice in one session with identical fixtures and
identical code:

| Sweep | Credits dropped |
|---|---|
| run A | `x07`, `x15`, `r01`, `r02`, `r03` (5) |
| run B | `c07`, `c14`, `x07`, `x14`, `x15`, `r01`, `r02`, `r03` (8) |

The bucket index is `timestampMs / 300000`, so which members of a
same-amount/same-sender group collide depends on when the sweep started relative
to the 5-minute wall-clock boundary. Different payments are sacrificed on
different days. This is the difference between "8 of 10 every day" and "8 of 10
sometimes 6 of 10", and it is why the loss is easy to miss in the field.

## 3. Count — valid payments silently dropped

Whole run, 100 replayed events:

| Terminal | Count | Valid payment lost? |
|---|---|---|
| `QUEUED` (settle artifact) | 56 | no |
| `SPOKEN` | 4 | no |
| `DEDUPED` | 15 | **9** (14 bucket collisions − 5 on non-payment/ambiguous fixtures) |
| `DROPPED` | 25 | 0 (all on debits, non-payments or malformed bodies) |

Corpus only (66 fixtures):

| Kind | Announced | `DEDUPED` | `DROPPED` | Lost |
|---|---|---|---|---|
| Credit (37) | 32 | 5 | 0 | **5** |
| Debit (7) | 0 | 0 | 7 | 0 (correct) |
| NonPayment (12) | 0 | 0 | 12 | 0 (correct) |
| Ambiguous (10) | 3 | 5 | 2 | **up to 4** (see below) |

**5 of 37 clear credits (13.5 %) were silently dropped, all at the `DEDUPED`
hop with `dedup-amount-bucket-collision` (`ref=0`).** Zero were lost at parse,
direction, confidence or amount gates — every `DROPPED` row in the corpus is a
debit, an offer, or a malformed body.

The 5 `DEDUPED` *ambiguous* fixtures are almost all the same defect: `a05`,
`a07`, `a08` and `a10` are all "₹500 … Rahul Sharma" variants, so they collide
with `c01`/`x14` in the same bucket. Only `a04` (cashback credited to wallet) is
a genuine non-payment. Those four are held back only by a title word ("received")
and a trailing fee clause, so treat the corpus loss as **5 clear + up to 4
probable = 9 of 47 parseable credits**.

Reasons seen across the whole run:

| Hop / reason | Count | Assessment |
|---|---|---|
| `DEDUPED` · `dedup-amount-bucket-collision` (ref=0) | 14 | **defect** — 9 on valid payments |
| `DEDUPED` · `dedup-reference-replay` (ref=1) | 1 | correct (`z06` carries a `Txn ID`) |
| `DROPPED` · `parse-null-money-marker` (extrasSeen=title+text) | 8 | correct |
| `DROPPED` · `gate-direction SENT` | 5 | correct (debits) |
| `DROPPED` · `parse-null` (extrasSeen=title+text / title / none) | 6 | correct |
| `DROPPED` · `gate-confidence MEDIUM` | 3 | correct |
| `DROPPED` · `gate-amount` | 2 | correct (`z01` collapsed marker-only, `a09` no digits) |
| `DROPPED` · `gate-direction UNKNOWN` | 1 | correct |

## 4. Exonerated by this run

- **Bursts.** `paymentBurst` / `burstWithFullBudget`: 10 payments 1.5 s apart,
  `spoken=10/10 dropped=0`, captured and queued in 1363 ms, TTS warm. Rapid
  back-to-back payments are not the problem.
- **Announcements complete.** `announcementCompletesWithFullBudget` reached
  `PARSED > QUEUED > STORED > SPOKEN` with `ttsReady=true`. The `QUEUED`
  verdicts in the 66-fixture sweep are a harness settle artifact —
  `AnnouncementSpeaker.speak()` holds a wake lock for up to 45 s and the sweep
  budget is 2.5 s.
- **No false alarms.** Zero debits, offers or requests were announced. The
  safety invariant held for the whole corpus.
- **Identical reposts** are correctly suppressed (and, today, by the same bucket
  that eats real payments).
- **Degraded states**: empty/null/oversized/truncated bodies are dropped cleanly
  at the parse gate with no crash.
- **Foreign packages** are dropped at the gate.

## 5. Per-fixture results

`QUEUED` means "reached the speaker request and was stored"; the sweep's 2.5 s
budget was too short to observe `SPOKEN` (see §4).

### 5.1 The 66-fixture corpus

| Fixture | Kind | Terminal | Reason | Hop chain | Note |
|---|---|---|---|---|---|
| `c01` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c02` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c03` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c04` | Credit | QUEUED | | PARSED>QUEUED>STORED | real 'paid you' credit form |
| `c05` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c06` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c07` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c08` | Credit | QUEUED | | PARSED>QUEUED>STORED | amount on line 2 |
| `c09` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c10` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c11` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c12` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c13` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `c14` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `x01` | Credit | QUEUED | | PARSED>QUEUED>STORED | Rs. with dot |
| `x02` | Credit | QUEUED | | PARSED>QUEUED>STORED | INR spelled out |
| `x03` | Credit | QUEUED | | PARSED>QUEUED>STORED | lakh grouping |
| `x04` | Credit | QUEUED | | PARSED>QUEUED>STORED | crore grouping |
| `x05` | Credit | QUEUED | | PARSED>QUEUED>STORED | amount in the TITLE, sender in text |
| `x06` | Credit | QUEUED | | PARSED>QUEUED>STORED | no title at all |
| `x07` | Credit | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | collapsed text, full line in bigText |
| `x08` | Credit | QUEUED | | PARSED>QUEUED>STORED | payment line only in bigText |
| `x09` | Credit | QUEUED | | PARSED>QUEUED>STORED | payment line only in subText |
| `x10` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `x11` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `x12` | Credit | QUEUED | | PARSED>QUEUED>STORED | lower-case title |
| `x13` | Credit | QUEUED | | PARSED>QUEUED>STORED | upper case throughout |
| `x14` | Credit | QUEUED | | PARSED>QUEUED>STORED | trailing full stop |
| `x15` | Credit | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | — |
| `x16` | Credit | QUEUED | | PARSED>QUEUED>STORED | amount in text, sender in subText |
| `x17` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `x18` | Credit | QUEUED | | PARSED>QUEUED>STORED | space after the rupee sign |
| `x19` | Credit | QUEUED | | PARSED>QUEUED>STORED | paise |
| `x20` | Credit | QUEUED | | PARSED>QUEUED>STORED | — |
| `r01` | Credit | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | — |
| `r02` | Credit | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | same amount, same sender — a genuine second payment |
| `r03` | Credit | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | third in the same 5-minute bucket |
| `d01` | Debit | DROPPED | gate-direction SENT | PARSED>DROPPED | — |
| `d02` | Debit | DROPPED | gate-confidence MEDIUM | PARSED>DROPPED | — |
| `d03` | Debit | DROPPED | gate-direction SENT | PARSED>DROPPED | — |
| `d04` | Debit | DROPPED | gate-direction SENT | PARSED>DROPPED | — |
| `d05` | Debit | DROPPED | gate-confidence MEDIUM | PARSED>DROPPED | — |
| `d06` | Debit | DROPPED | gate-direction SENT | PARSED>DROPPED | — |
| `d07` | Debit | DROPPED | gate-direction SENT | PARSED>DROPPED | — |
| `n01` | NonPayment | DROPPED | parse-null extrasSeen=title+text | PARSED>DROPPED | — |
| `n02` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `n03` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | currency marker, no payment |
| `n04` | NonPayment | DROPPED | parse-null extrasSeen=title+text | PARSED>DROPPED | — |
| `n05` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `n06` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `n07` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `n08` | NonPayment | DROPPED | parse-null extrasSeen=title+text | PARSED>DROPPED | — |
| `n09` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `n10` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `n11` | NonPayment | DROPPED | gate-direction UNKNOWN | PARSED>DROPPED | — |
| `n12` | NonPayment | DROPPED | parse-null-money-marker extrasSeen=title+text | PARSED>DROPPED | — |
| `a01` | Ambiguous | QUEUED | | PARSED>QUEUED>STORED | — |
| `a02` | Ambiguous | SPOKEN | engine=warm | PARSED>QUEUED>STORED>SPOKEN | — |
| `a03` | Ambiguous | DROPPED | gate-confidence MEDIUM | PARSED>DROPPED | — |
| `a04` | Ambiguous | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | — |
| `a05` | Ambiguous | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | — |
| `a06` | Ambiguous | QUEUED | | PARSED>QUEUED>STORED | — |
| `a07` | Ambiguous | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | no directional verb at all |
| `a08` | Ambiguous | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | — |
| `a09` | Ambiguous | DROPPED | gate-amount | PARSED>DROPPED | marker with no digits |
| `a10` | Ambiguous | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED | no sender at all |

### 5.2 Scenarios

| Scenario | Fixture | Terminal | Reason | Hop chain |
|---|---|---|---|---|
| announce-full-budget | c01 | SPOKEN | engine=warm | PARSED>QUEUED>STORED>SPOKEN |
| burst-10-full-budget | burst01 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst02 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst03 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst04 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst05 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst06 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst07 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst08 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst09 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-full-budget | burst10 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst01 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst02 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst03 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst04 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst05 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst06 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst07 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst08 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst09 | QUEUED | | PARSED>QUEUED>STORED |
| burst-10-in-15s | burst10 | QUEUED | | PARSED>QUEUED>STORED |
| empty-or-null-body | z02 | DROPPED | parse-null extrasSeen=none | PARSED>DROPPED |
| empty-or-null-body | z03 | DROPPED | parse-null extrasSeen=none | PARSED>DROPPED |
| empty-or-null-body | z04 | DROPPED | parse-null extrasSeen=title | PARSED>DROPPED |
| fingerprint-bucket-boundary | b0 | SPOKEN | engine=warm | PARSED>QUEUED>STORED>SPOKEN |
| fingerprint-bucket-boundary | b240000 | SPOKEN | engine=warm | PARSED>QUEUED>STORED>SPOKEN |
| fingerprint-bucket-boundary | b299000 | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED |
| fingerprint-bucket-boundary | b301000 | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED |
| fingerprint-bucket-boundary | b320000 | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED |
| identical-repost | c01 | QUEUED | | PARSED>QUEUED>STORED |
| identical-repost | c01 (reposted) | DEDUPED | dedup-amount-bucket-collision ref=0 | PARSED>DEDUPED |
| marker-only-collapsed | z01 | DROPPED | gate-amount | PARSED>DROPPED |
| truncated-or-huge-body | z05 | QUEUED | | PARSED>QUEUED>STORED |
| truncated-or-huge-body | z06 | DEDUPED | dedup-reference-replay ref=1 | PARSED>DEDUPED |
| `foreign-package` | `c01` | DROPPED | foreign package gate | `PARSED>DROPPED` |
| `same-amount-same-sender-within-5min` | `r01` | QUEUED | | `PARSED>QUEUED>STORED` |
| `same-amount-same-sender-within-5min` | `r02` | DEDUPED | `dedup-amount-bucket-collision ref=0` | `PARSED>DEDUPED` |
| `same-amount-same-sender-within-5min` | `r03` | DEDUPED | `dedup-amount-bucket-collision ref=0` | `PARSED>DEDUPED` |

The last two scenarios' rows come from an earlier run of the same harness whose
logcat buffer was truncated; the JUnit XML for the authoritative 11-test run
covers both (`nonGooglePayPackageIsDropped`, `threeSameAmountPaymentsWithinFiveMinutes`)
and the terminal outcomes reproduce identically across three runs.

## 6. Reproducing

```bash
./gradlew --offline :app:connectedDebugAndroidTest
```

`verifyInstrumentationResults` (wired as a finalizer of the connected task)
fails the build when the run executes zero tests or reports a failure — a filter
that matches nothing used to produce a green `BUILD SUCCESSFUL`. Rows are
`println`ed to logcat as `REPLAY-ROW {...}` because `connectedAndroidTest`
uninstalls the app (and its exported JSONL) when it finishes.

```bash
adb -s emulator-5554 logcat -d | grep REPLAY-ROW | sed 's/.*System.out: REPLAY-ROW //'
```

## 7. Open limits

1. No real-device capture trace exists. The two payments the owner did not hear
   were never observed entering the pipeline.
2. `UPLOADED`, `FCM_SENT`, `FCM_RECEIVED` are untested by this harness — the
   employee-side delivery half of the 8-of-10 report is untouched by these
   findings.
3. The corpus is synthetic. Sender names and amounts are fixtures, not the
   owner's real traffic.
---

# Step 4 — the fix, and what it changed

The §1 defect is fixed. This section records the new key, the cross-channel
merge rules that replaced the merge side-effect of the old key, and the
before/after measured on the same harness.

## 8. The new dedupe key

`Fingerprinter.captureFingerprint()` no longer contains a time bucket. Three
tiers, most authoritative first:

| Tier | Payload | Collides when |
|---|---|---|
| `REF\|` | `REFERENCE.trim().uppercase() \| amountMinor` | the same UPI/Txn reference appears again, on any channel |
| `SRC\|` | `package \| notificationId \| notificationTag \| postedAtMs \| amountMinor` | the **same system event** is presented again — OS re-delivery, or the listener-rebind snapshot |
| `TXT\|` | `package \| normalize(title) \| normalize(text) \| postedAtMs \| amountMinor` | the same content is presented again at the exact same millisecond (SMS, which has no sbn identity) |

`CaptureEvent` gained `notificationTag`, threaded from
`StatusBarNotification.tag` in `PayVoiceNotificationListener`. `senderName` and
`captureSource` are no longer inputs: reconciling two channels for one payment
is now an explicit, rule-bound decision rather than a side effect of hashing a
wall clock.

The reason token `dedup-amount-bucket-collision` is gone — it can no longer
occur. The two that remain are `dedup-reference-replay` and
`dedup-source-replay`.

## 9. The cross-channel merge — `CrossChannelMerger`

Removing the amount+window key means a GPay notification and a bank SMS for one
payment key differently and would both announce. The merge closes that gap under
four rules, all of which must hold; `CROSS_CHANNEL_MERGE_WINDOW_MS = 90_000L`:

1. same amount;
2. different channels;
3. within 90 seconds;
4. neither side carries a UPI/Txn reference — if either does, the reference
   decides, and the reference-keyed dedupe already had its chance.

When they hold: announce once, on whichever arrived first; the second signal is
kept in history marked `mergedWithEventId` → the event it merged into, and is
excluded from the Payments totals. **Two events from the same channel are never
merged** without a shared reference. Every decision — merged or not — writes a
`MERGED` trace row with a countable token (`merge-cross-channel`,
`merge-keep-same-channel`, `merge-keep-outside-window`,
`merge-keep-reference-present`, …).

The failure direction is fixed: every rule that cannot be evaluated confidently
returns *announce*. A duplicate announcement is a nuisance; a missed payment is
the bug this work exists to fix.

Schema v5 → v6 adds `captureSource` / `postedAtMs` / `hasReference` to
`processed_events` (with a composite index on `(amountMinor, postedAtMs)`, the
exact shape of the merge-candidate query) and `mergedWithEventId` to
`announcement_history`. Both tables are rebuilt and every row copied — see
`Migrations.MIGRATION_5_6` and the reason in that file's header.

## 10. Before / after on the same harness

Emulator, Pixel 8 Pro AVD API 34. **Same fixture corpus, same pipeline, same
run shape as §1–§4.**

| Measurement | Before (Step 3) | After (Step 4) |
|---|---|---|
| Credit fixtures reaching the speaker | 32 QUEUED / **5 DEDUPED** / 0 DROPPED | **37 QUEUED / 0 DEDUPED / 0 DROPPED** |
| `dedup-amount-bucket-collision` count | 14 | **0** (the reason cannot occur) |
| `creditDropped` reported by the harness | 5 | **0** |
| Bucket boundary t+0 / +240 / +299 / +301 / +320 s | SPOKEN, SPOKEN, **DEDUPED, DEDUPED, DEDUPED** | **SPOKEN ×5** |
| Three ₹500 payments, 59 s apart | 1 announced | **3 announced** |
| Debit / NonPayment / Ambiguous | 0 false announcements | **0 false announcements** |
| 10-payment burst, 1.5 s apart | 10/10 | **10/10** |

New regression tests (`PaymentReplayHarness`): `sameAmountPaymentsWithinTheOldBucketAllAnnounce`
(3/3 spoken), `sameSystemEventReplayedTwiceAnnouncesOnce` (SPOKEN then DEDUPED),
`sameContentLaterIsNotTreatedAsAReplay`, `crossChannelDuplicateAnnouncesOnce`
(one announcement, history marked), `sameChannelNearDuplicateIsNotMerged`.
`CrossChannelMergerTest` covers each rule individually plus a 432-case sweep
asserting that **every** pair which is not an all-rules-pass announces.

## 11. What Step 4 does NOT claim

1. **It does not claim the owner's phone is fixed.** Everything in §10 is
   emulator output with synthetic fixtures. There is still no real-device capture
   trace of the two missing payments.
2. **It does not claim a better capture rate.** The capture hop (does a real
   bank/GPay notification reach the pipeline at all) was not exercised, and
   §0 of this document still stands.
3. **It does not touch the delivery half.** `UPLOADED` / `FCM_SENT` /
   `FCM_RECEIVED` remain untested; the employee-side fan-out is unchanged.
4. **The SMS channel is not live.** SMS capture was removed in `535412c`, so
   `CaptureSource.SMS_KOTAK` / `SMS_BANK` are still enum values nothing
   constructs. The merge rules are channel-agnostic and unit-tested, but the
   end-to-end harness test drives a second *notification* channel because an
   SMS-shaped event dies at `parserForPackage` before reaching the merge.
5. **The corpus is still synthetic.** These are fixtures, not the owner's
   traffic.

package com.vivekray898.payvoice.replay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vivekray898.payvoice.AppContainer
import com.vivekray898.payvoice.core.model.CaptureEvent
import com.vivekray898.payvoice.core.model.CaptureSource
import com.vivekray898.payvoice.core.trace.PaymentTrace
import com.vivekray898.payvoice.core.trace.PaymentTrace.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * STEP 3 replay harness.
 *
 * Feeds real-format Google Pay notification payloads through the *production*
 * [com.vivekray898.payvoice.PaymentPipeline] — the same parse, gate, fingerprint,
 * dedupe and speak code that runs on a live device — then reads the Step 2 debug
 * trace table back to find out where each payment ended up.
 *
 * This is deliberately not a mock/unit test. The question is "where do real
 * payments die", and that answer lives in the real parser's behaviour on real
 * notification shapes and in the real dedupe store's collision rules.
 *
 * Design rules for this file:
 *  - **Nothing here encodes a wanted answer.** A fixture the corpus says is a
 *    [PaymentFixtures.Kind.Credit] that the pipeline drops is a *finding*, not a
 *    broken test. Assertions are limited to safety invariants that must hold no
 *    matter what, plus "the run completes".
 *  - **TTS honesty.** An emulator with no speech engine parks every utterance at
 *    [Outcome.QUEUED]. The harness reports TTS availability and treats QUEUED
 *    under "no engine" as "reached the speaker", but it says so out loud rather
 *    than silently counting it as a delivery.
 *
 * Output: `payvoice-replay.jsonl` in the app's external files dir, one line per
 * replay, which is what `docs/PAYMENT_PIPELINE_FINDINGS.md` is written from.
 */
@RunWith(AndroidJUnit4::class)
class PaymentReplayHarness {

    private lateinit var container: AppContainer
    private val findings = mutableListOf<String>()
    private var ttsReady = false

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        container = AppContainer(app)
        // Forces the debug-gated trace sink to install before the first hop.
        check(container.traceEnabled) {
            "harness requires a debuggable build; the trace sink was not installed"
        }
        runBlocking {
            // The dedupe store MUST be emptied too, not just the trace rows: a
            // fingerprint left behind by the corpus pass would make every later
            // scenario report DEDUPED and the near-duplicate tests meaningless.
            // `deleteOlderThan(MAX_VALUE)` empties it through the existing public
            // API, so the harness needs no test-only production DAO method.
            container.database.traceEventDao().clear()
            container.database.processedEventDao().deleteOlderThan(Long.MAX_VALUE)
            container.speaker.warmUp()
        }
        ttsReady = container.speaker.isEngineReady()
    }

    @After
    fun tearDown() {
        writeReport()
    }

    // ------------------------------------------------------------------ core

    /**
     * Replays one fixture through the real pipeline and returns the full hop
     * chain it produced.
     *
     * Trace rows are written asynchronously (the sink launches on Dispatchers.IO
     * so tracing never stalls the payment path), so the chain is polled rather
     * than read once.
     */
    private fun replay(
        fixture: PaymentFixtures.Fixture,
        packageName: String = GPAY,
        settleMs: Long = SETTLE_FAST_MS,
        postedAtMs: Long = System.currentTimeMillis(),
    ): Replay {
        val postedAt = postedAtMs
        val cid = PaymentTrace.newCorrelationId()
        runBlocking {
            container.pipeline.handleCapture(
                correlationId = cid,
                event = CaptureEvent(
                    captureSource = CaptureSource.GPAY_NOTIFICATION,
                    originId = packageName,
                    title = fixture.title,
                    body = fixture.text.orEmpty(),
                    bigText = fixture.bigText,
                    subText = fixture.subText,
                    notificationId = fixture.id.hashCode(),
                    postedAtMs = postedAt,
                ),
            )
        }
        val rows = awaitSettled(cid, settleMs)
        return Replay(fixture, cid, rows)
    }

    /**
     * Replays one fixture on a chosen channel. The SMS channels have no live
     * receiver yet, so this drives `handleCapture` directly with the channel's
     * own origin id — which is exactly what such a receiver will do.
     */
    private fun replayAsChannel(
        fixture: PaymentFixtures.Fixture,
        channel: CaptureSource,
        originId: String,
        notificationId: Int = -1,
        settleMs: Long = SETTLE_FAST_MS,
        postedAtMs: Long = System.currentTimeMillis(),
    ): Replay {
        val cid = PaymentTrace.newCorrelationId()
        runBlocking {
            container.pipeline.handleCapture(
                correlationId = cid,
                event = CaptureEvent(
                    captureSource = channel,
                    originId = originId,
                    title = fixture.title,
                    body = fixture.text.orEmpty(),
                    bigText = fixture.bigText,
                    subText = fixture.subText,
                    notificationId = notificationId,
                    postedAtMs = postedAtMs,
                ),
            )
        }
        return Replay(fixture, cid, awaitSettled(cid, settleMs))
    }

    /** One recorded hop, with the reason the pipeline attached to it. */
    private data class Hop(
        val outcome: String,
        val reason: String?,
        val detail: String?,
    )

    /**
     * Waits for the chain to stop changing.
     *
     * The pipeline has no completion signal, and `AnnouncementSpeaker.speak`
     * holds a wake lock for up to 45 s waiting on the utterance, so "no new hop
     * for [settleMs]" is the only sound completion signal available here. Two
     * budgets are used: a short one for the 66-fixture sweep, and the full one
     * for the handful of fixtures the deep-dive test needs to be certain about.
     */
    private fun awaitSettled(cid: String, settleMs: Long): List<Hop> {
        val deadline = System.currentTimeMillis() + SETTLE_CEILING_MS
        var last: List<Hop> = emptyList()
        var unchangedSince = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val rows = runBlocking { container.database.traceEventDao().forCorrelation(cid) }
            val now = rows.map { Hop(it.outcome, it.reason, it.detail) }
            if (now != last) {
                last = now
                unchangedSince = System.currentTimeMillis()
            } else if (last.isNotEmpty() &&
                System.currentTimeMillis() - unchangedSince >= settleMs
            ) {
                return last
            }
            runCatching { Thread.sleep(100) }
        }
        return last
    }

    /** SPOKEN, or QUEUED parked on an engine that is not there. */
    private fun Replay.reachedSpeaker(): Boolean =
        chain.contains(Outcome.SPOKEN.name) ||
            (!ttsReady && chain.contains(Outcome.QUEUED.name))

    /** Where the payment stopped, for the findings table. */
    private fun Replay.terminal(): String {
        val last = hops.lastOrNull() ?: return "NO TRACE"
        return when {
            // A merge is terminal when it is the LAST hop: the event was a
            // second signal for a payment already spoken, so nothing follows.
            // A merge anywhere earlier is just the decision record, and the
            // payment carried on to the announcement.
            last.outcome == Outcome.MERGED.name &&
                last.reason == "merge-cross-channel" -> "MERGED (cross-channel duplicate)"
            chain.contains(Outcome.SPOKEN.name) -> "SPOKEN"
            chain.contains(Outcome.DROPPED.name) -> "DROPPED"
            chain.contains(Outcome.DEDUPED.name) -> "DEDUPED"
            chain.contains(Outcome.QUEUED.name) -> "QUEUED (no terminal hop in settle window)"
            chain.contains(Outcome.PARSED.name) -> "PARSED (no terminal hop)"
            else -> "INCOMPLETE"
        }
    }

    /** The reason the pipeline attached to the terminal hop, for the table. */
    private fun Replay.terminalReason(): String {
        val terminal = hops.lastOrNull {
            it.outcome == Outcome.DROPPED.name ||
                it.outcome == Outcome.DEDUPED.name ||
                it.outcome == Outcome.MERGED.name ||
                it.outcome == Outcome.SPOKEN.name
        } ?: return ""
        return listOfNotNull(
            terminal.reason?.takeIf { it.isNotBlank() },
            terminal.detail?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    private fun record(replay: Replay, scenario: String) {
        val line = json(
            "scenario" to scenario,
            "fixture" to replay.fixture.id,
            "kind" to replay.fixture.kind.name,
            "note" to replay.fixture.note,
            "terminal" to replay.terminal(),
            "reason" to replay.terminalReason(),
            "chain" to replay.chain.joinToString(">"),
        )
        findings += line
        // Also to stdout: `connectedAndroidTest` uninstalls the app when it
        // finishes, which would otherwise take the exported file with it.
        println("REPLAY-ROW $line")
    }

    private class Replay(
        val fixture: PaymentFixtures.Fixture,
        val cid: String,
        val hops: List<Hop>,
    ) {
        val chain: List<String> get() = hops.map { it.outcome }
    }

    // ---------------------------------------------------------- 1. the corpus

    /**
     * The whole corpus in one pass. Every outcome is recorded; the assertions
     * are the safety invariants, not "everything announced".
     */
    @Test
    fun replayFullCorpus() {
        val byKindThenTerminal = linkedMapOf<String, Int>()

        PaymentFixtures.all.forEach { fixture ->
            val r = replay(fixture)
            record(r, "corpus")
            val bucket = "${fixture.kind}/${r.terminal()}"
            byKindThenTerminal[bucket] = (byKindThenTerminal[bucket] ?: 0) + 1

            // Safety invariant 1: money leaving, and promos/nudges/requests, must
            // never reach the speaker. A violation is a false announcement.
            if (fixture.kind == PaymentFixtures.Kind.Debit ||
                fixture.kind == PaymentFixtures.Kind.NonPayment
            ) {
                assertTrue(
                    "${fixture.id} (${fixture.kind}) reached the speaker: ${r.chain}",
                    !r.reachedSpeaker(),
                )
            }
        }

        val summary = byKindThenTerminal.entries.joinToString("  ") { "${it.key}=${it.value}" }
        println("REPLAY-CORPUS ttsReady=$ttsReady total=${PaymentFixtures.all.size} :: $summary")
        assertTrue("corpus is smaller than 50", PaymentFixtures.all.size >= 50)
    }

    // --------------------------------------------------- 2. near-duplicates

    /**
     * The headline scenario: three genuine ₹500 payments from the same person
     * inside the fingerprint's 5-minute bucket. How many actually announce is
     * the single largest suspect for "8 of 10 captured".
     */
    @Test
    fun threeSameAmountPaymentsWithinFiveMinutes() {
        val chain = PaymentFixtures.referenceLess.map { fixture ->
            val r = replay(fixture)
            record(r, "same-amount-same-sender-within-5min")
            r.terminal()
        }
        println("REPLAY-DUPE same-amount-same-sender :: ${chain.joinToString(" > ")}")
    }

    /**
     * The same content arriving again at a LATER post time is, under the new
     * key, a different source event — so it announces.
     *
     * This is the other half of the change from the amount+time bucket. The
     * old key called these two the same payment because they fell in the same
     * five-minute window, and that is precisely how real payments were being
     * lost. Suppression is now reserved for a genuine re-delivery — the same
     * sbn identity re-presented at the same post time, covered by
     * [sameSystemEventReplayedTwiceAnnouncesOnce].
     */
    @Test
    fun sameContentLaterIsNotTreatedAsAReplay() {
        val f = PaymentFixtures.plainCredits.first()
        val base = System.currentTimeMillis()
        val first = replay(f, postedAtMs = base, settleMs = SETTLE_DEEP_MS)
        record(first, "same-content-later-a")
        val second = replay(f, postedAtMs = base + 30_000, settleMs = SETTLE_DEEP_MS)
        record(second, "same-content-later-b")
        println("REPLAY-DUPE same-content-later :: ${first.terminal()} | ${second.terminal()}")
        assertTrue("first must announce", first.reachedSpeaker())
        assertTrue(
            "the same content at a later post time cannot be proven to be a " +
                "re-delivery, and an unsure miss is the worse error: ${second.chain}",
            second.reachedSpeaker(),
        )
    }

    // -------------------------------------------------------------- 3. burst

    /** Five to ten payments back to back, as at a shop counter. */
    @Test
    fun paymentBurst() {
        val base = System.currentTimeMillis()
        val burst = PaymentFixtures.burst(10, gapMs = 1_500L, basePostedAtMs = base)
        var announced = 0
        burst.forEach { (_, fixture) ->
            val r = replay(fixture)
            record(r, "burst-10-in-15s")
            if (r.reachedSpeaker()) announced++
        }
        println("REPLAY-BURST announced=$announced/10 :: ${burst.size} fixtures")
    }

    // -------------------------------------------- 4. hostile / degraded inputs

    @Test
    fun nonGooglePayPackageIsDropped() {
        val r = replay(PaymentFixtures.plainCredits.first(), packageName = "com.whatsapp")
        record(r, "foreign-package")
        assertTrue("a WhatsApp payment reached the speaker", !r.reachedSpeaker())
    }

    @Test
    fun paymentLineHiddenFromEveryExtraIsDropped() {
        val r = replay(
            PaymentFixtures.Fixture(
                id = "z01",
                kind = PaymentFixtures.Kind.Credit,
                title = "Payment received",
                text = "₹",
                note = "collapsed notification with nothing but the marker",
            ),
        )
        record(r, "marker-only-collapsed")
    }

    @Test
    fun emptyAndNullBodiesDoNotCrash() {
        listOf(
            PaymentFixtures.Fixture("z02", PaymentFixtures.Kind.Ambiguous, null, ""),
            PaymentFixtures.Fixture("z03", PaymentFixtures.Kind.Ambiguous, null, null),
            PaymentFixtures.Fixture(
                "z04",
                PaymentFixtures.Kind.Credit,
                "Payment received",
                null,
                bigText = null,
                subText = null,
            ),
        ).forEach { f ->
            record(replay(f), "empty-or-null-body")
        }
    }

    /** Extremely long text — notification bodies get truncated by the platform. */
    @Test
    fun truncatedAndHugeBodies() {
        val long = "₹750 received from Rahul Sharma • UPI Ref 512345678901 " +
            "and a very long trailing description ".repeat(40)
        listOf(
            PaymentFixtures.Fixture(
                "z05",
                PaymentFixtures.Kind.Credit,
                "Payment received",
                long,
                note = "over-long body",
            ),
            PaymentFixtures.Fixture(
                "z06",
                PaymentFixtures.Kind.Credit,
                "Payment received",
                long.take(120),
                note = "platform-truncated to 120 chars",
            ),
        ).forEach { f -> record(replay(f), "truncated-or-huge-body") }
    }

    // ------------------------------------------------------------- 5. report

    private fun json(vararg pairs: Pair<String, String>): String = pairs.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ",",
    ) { (k, v) -> "\"$k\":\"${esc(v)}\"" }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun writeReport() {
        val dir = ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null)
            ?: return
        File(dir, "payvoice-replay.jsonl").writeText(
            findings.joinToString("\n", postfix = "\n")
        )
        val droppedCredits = findings
            .filter { it.contains("\"kind\":\"Credit\"") && it.contains("DROPPED") }
            .size
        println(
            "REPLAY-TOTAL ttsReady=$ttsReady findings=${findings.size} " +
                "creditDropped=$droppedCredits"
        )
    }

    /** Fires a fixture without waiting, for burst tests that drain afterwards. */
    private fun replayFast(fixture: PaymentFixtures.Fixture): Replay {
        val cid = PaymentTrace.newCorrelationId()
        runBlocking {
            container.pipeline.handleCapture(
                correlationId = cid,
                event = CaptureEvent(
                    captureSource = CaptureSource.GPAY_NOTIFICATION,
                    originId = GPAY,
                    title = fixture.title,
                    body = fixture.text.orEmpty(),
                    bigText = fixture.bigText,
                    subText = fixture.subText,
                    notificationId = fixture.id.hashCode(),
                    postedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        runCatching { Thread.sleep(120) }
        val rows = runBlocking { container.database.traceEventDao().forCorrelation(cid) }
        return Replay(fixture, cid, rows.map { Hop(it.outcome, it.reason, it.detail) })
    }

    /**
     * Deep dive: does an announcement EVER complete on this device, given the
     * full wake-lock budget rather than the sweep's short one? This separates
     * "the pipeline lost it" from "the utterance was still in flight when the
     * sweep looked".
     */
    @Test
    fun announcementCompletesWithFullBudget() {
        runBlocking {
            container.database.traceEventDao().clear()
            container.database.processedEventDao().deleteOlderThan(Long.MAX_VALUE)
        }
        val f = PaymentFixtures.plainCredits.first()
        val r = replay(f, settleMs = SETTLE_DEEP_MS)
        record(r, "announce-full-budget")
        println(
            "REPLAY-DEEP ttsReady=$ttsReady engineReadyNow=${container.speaker.isEngineReady()} " +
                "hasPending=${container.speaker.hasPending} chain=${r.chain.joinToString(">")}"
        )
    }

    /**
     * The burst, given enough budget to finish.
     *
     * The fast sweep reports a burst as all-QUEUED, but that only says the
     * utterances were still in flight. This fires ten payments a second and a
     * half apart and then waits for the speaker to drain, because a shop
     * counter is exactly where a burst happens and "how many of ten did the
     * customer actually hear" is the question the whole investigation is about.
     */
    @Test
    fun burstWithFullBudget() {
        val burst = PaymentFixtures.burst(10, gapMs = 1_500L, basePostedAtMs = System.currentTimeMillis())
        val cids = mutableListOf<String>()
        val t0 = System.currentTimeMillis()
        burst.forEach { (_, fixture) ->
            val r = replayFast(fixture)
            cids += r.cid
            record(r, "burst-10-full-budget")
        }
        val spokenAfter = System.currentTimeMillis() - t0
        // The speaker serialises utterances behind speakMutex; give it room.
        runCatching { Thread.sleep(BURST_DRAIN_MS) }
        val chains = cids.map {
            runBlocking { container.database.traceEventDao().forCorrelation(it) }.map { r -> r.outcome }
        }
        val spoken = chains.count { it.contains(Outcome.SPOKEN.name) }
        val overwritten = chains.count { it.contains(Outcome.DROPPED.name) }
        println(
            "REPLAY-BURST-DEEP spoken=$spoken/10 dropped=$overwritten " +
                "captured+queuedIn=${spokenAfter}ms :: " +
                chains.joinToString(" | ") { it.joinToString(">") }
        )
    }

    /**
     * Where exactly the 5-minute cross-channel bucket starts eating distinct
     * payments. Same amount, same sender, nothing else to tell them apart.
     */
    @Test
    fun fingerprintBucketBoundary() {
        val base = System.currentTimeMillis()
        val outcomes = listOf(0L, 240_000L, 299_000L, 301_000L, 320_000L).map { offset ->
            val fixture = PaymentFixtures.Fixture(
                id = "b$offset",
                kind = PaymentFixtures.Kind.Credit,
                title = "Payment received",
                text = "₹500 received from Rahul Sharma",
                note = "t+${offset / 1000}s",
            )
            val r = replay(fixture, postedAtMs = base + offset, settleMs = SETTLE_DEEP_MS)
            record(r, "fingerprint-bucket-boundary")
            "t+${offset / 1000}s=${r.terminal()}"
        }
        println("REPLAY-BOUNDARY :: ${outcomes.joinToString("  ")}")
    }

    // --------------------------------------- 6. Step 4 regression: the two
    // payments the old amount+5-minute-bucket key used to collapse into one.

    /**
     * The regression this whole change exists for, replayed end to end through
     * the real pipeline: three genuine ₹500 payments from the same person,
     * 59 seconds apart. The old fingerprint keyed them all to the same 5-minute
     * bucket, so only the first ever reached the speaker.
     *
     * Asserts what must now be true rather than what merely happened to be true
     * in Step 3 — this is a fix, so its regression test states the fix.
     */
    @Test
    fun sameAmountPaymentsWithinTheOldBucketAllAnnounce() {
        val base = System.currentTimeMillis()
        val offsets = listOf(0L, 59_000L, 118_000L)
        val outcomes = offsets.map { offset ->
            val r = replay(
                PaymentFixtures.Fixture(
                    id = "s${offset / 1000}",
                    kind = PaymentFixtures.Kind.Credit,
                    title = "Payment received",
                    text = "₹500 received from Rahul Sharma",
                    note = "t+${offset / 1000}s",
                ),
                postedAtMs = base + offset,
                settleMs = SETTLE_DEEP_MS,
            )
            record(r, "step4-same-amount-no-bucket")
            r
        }
        val announced = outcomes.count { it.reachedSpeaker() }
        println("REPLAY-STEP4 same-amount announced=$announced/3 :: " + outcomes.joinToString(" | ") { it.terminal() })
        assertEquals(
            "two legitimate same-amount payments must BOTH announce; " +
                outcomes.joinToString(" | ") { it.terminal() },
            3,
            announced,
        )
    }

    /**
     * The guarantee the new key must still give: one system event delivered
     * twice (listener-rebind snapshot, platform double-post) announces once.
     * Same sbn id, tag and post time — the SRC tier's whole job.
     */
    @Test
    fun sameSystemEventReplayedTwiceAnnouncesOnce() {
        val f = PaymentFixtures.plainCredits.first()
        val postedAt = System.currentTimeMillis()
        val first = replay(f, settleMs = SETTLE_DEEP_MS, postedAtMs = postedAt)
        record(first, "step4-same-event-replay")
        val second = replay(f, settleMs = SETTLE_DEEP_MS, postedAtMs = postedAt)
        record(second, "step4-same-event-replay")
        println("REPLAY-STEP4 same-event first=${first.terminal()} second=${second.terminal()}")
        assertTrue(
            "a true re-delivery of the same system event must not announce twice",
            first.reachedSpeaker(),
        )
        assertTrue(
            "a true re-delivery of the same system event must not announce twice",
            !second.reachedSpeaker(),
        )
    }

    /**
     * Cross-channel merge, end to end through the real pipeline: the same ₹500
     * arrives on GPay and then, 30s later, on a second capture channel. Rules 1
     * and 4 hold, rule 2 holds (different [CaptureSource]), rule 3 holds (30s <
     * 90s) — so exactly one announcement, and a marked history row for the
     * second signal.
     *
     * The second channel is [CaptureSource.OTHER_NOTIFICATION] on the same
     * package, not `SMS_KOTAK`: SMS capture was removed from this tree in
     * `535412c`, so an SMS-shaped event dies at `parserForPackage` before it
     * ever reaches the merge. The four rules are channel-agnostic and are
     * covered rule by rule in `CrossChannelMergerTest`; this proves the wiring
     * — store query, history row, trace token — with a channel the pipeline
     * really parses.
     */
    @Test
    fun crossChannelDuplicateAnnouncesOnce() {
        runBlocking {
            container.database.processedEventDao().deleteOlderThan(Long.MAX_VALUE)
        }
        val base = System.currentTimeMillis()
        val gpay = replay(
            PaymentFixtures.Fixture(
                id = "m1", kind = PaymentFixtures.Kind.Credit,
                title = "Payment received", text = "₹500 received from Rahul Sharma",
            ),
            postedAtMs = base, settleMs = SETTLE_DEEP_MS,
        )
        record(gpay, "step4-merge-first")
        val second = replayAsChannel(
            PaymentFixtures.Fixture(
                id = "m2", kind = PaymentFixtures.Kind.Credit,
                title = "Payment received", text = "₹500 received from Rahul Sharma",
            ),
            channel = CaptureSource.OTHER_NOTIFICATION, originId = GPAY,
            notificationId = 99, postedAtMs = base + 30_000, settleMs = SETTLE_DEEP_MS,
        )
        record(second, "step4-merge-second")

        val history = runBlocking { container.database.announcementDao().recent(50).first() }
        val merged = history.firstOrNull { it.mergedWithEventId != null }
        println(
            "REPLAY-STEP4 merge first=${gpay.terminal()} second=${second.terminal()} " +
                "historyMarked=${merged != null} token=${second.terminalReason()}",
        )
        assertTrue("the first signal must announce", gpay.reachedSpeaker())
        assertTrue(
            "the second signal on another channel inside the window must not " +
                "announce again: ${second.chain}",
            !second.reachedSpeaker(),
        )
        assertNotNull("the merged signal must stay in history, marked as merged", merged)
        assertTrue(
            "the merged row must point at the announcement it merged into",
            history.any { it.eventId == merged!!.mergedWithEventId && it.mergedWithEventId == null },
        )
    }

    /** Same channel, inside the window: rule 2 forbids the merge, so it announces. */
    @Test
    fun sameChannelNearDuplicateIsNotMerged() {
        runBlocking {
            container.database.processedEventDao().deleteOlderThan(Long.MAX_VALUE)
        }
        val base = System.currentTimeMillis()
        val a = replay(
            PaymentFixtures.Fixture("n1", PaymentFixtures.Kind.Credit, "Payment received", "₹500 received from Rahul Sharma"),
            postedAtMs = base, settleMs = SETTLE_DEEP_MS,
        )
        record(a, "step4-same-channel-a")
        val b = replay(
            PaymentFixtures.Fixture("n2", PaymentFixtures.Kind.Credit, "Payment received", "₹500 received from Rahul Sharma"),
            postedAtMs = base + 20_000, settleMs = SETTLE_DEEP_MS,
        )
        record(b, "step4-same-channel-b")
        println("REPLAY-STEP4 same-channel a=${a.terminal()} b=${b.terminal()}")
        assertTrue("first must announce", a.reachedSpeaker())
        assertTrue(
            "two events from the same channel must never merge without a shared " +
                "reference: ${b.chain}",
            b.reachedSpeaker(),
        )
    }

    private companion object {
        const val GPAY = "com.google.android.apps.nbu.paisa.user"

        /** Sweep budget: enough for a short utterance, not for a 45 s one. */
        const val SETTLE_FAST_MS = 2_500L

        /** Deep-dive budget: outlasts the speaker's 45 s wake-lock cap. */
        const val SETTLE_DEEP_MS = 50_000L

        /** Hard ceiling so one wedged fixture cannot hang the whole run. */
        const val SETTLE_CEILING_MS = 55_000L

        /** How long the speaker is given to drain a ten-payment burst. */
        const val BURST_DRAIN_MS = 75_000L
    }
}
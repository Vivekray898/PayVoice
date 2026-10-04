package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.dedup.CrossChannelMerger
import com.vivekray898.payvoice.core.dedup.CrossChannelMerger.Decision
import com.vivekray898.payvoice.core.dedup.CrossChannelMerger.Earlier
import com.vivekray898.payvoice.core.dedup.CrossChannelMerger.Incoming
import com.vivekray898.payvoice.core.dedup.CrossChannelMerger.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One test per merge rule, in the order `CrossChannelMerger.decide` applies
 * them. The rules exist to buy back the double announcement that removing the
 * amount+time-bucket key would otherwise cost — so the failure direction to
 * protect here is ANNOUNCING when unsure, never merging.
 */
class CrossChannelMergerTest {

    private val now = 1_782_000_000_000L

    private fun gpay(atMs: Long = now, ref: Boolean = false) = Incoming(
        amountMinor = 50000,
        postedAtMs = atMs,
        captureSource = "GPAY_NOTIFICATION",
        hasReference = ref,
    )

    private fun earlier(
        eventId: String = "evt_earlier",
        amountMinor: Long = 50000,
        atMs: Long = now - 30_000,
        captureSource: String = "SMS_KOTAK",
        ref: Boolean = false,
    ) = Earlier(eventId, amountMinor, atMs, captureSource, ref)

    private fun reasonOf(decision: Decision): Reason? =
        (decision as? Decision.Announce)?.reason

    // ---- rule 0: nothing to merge with ----

    @Test
    fun `no candidate announces`() {
        val d = CrossChannelMerger.decide(gpay(), earlier = null)
        assertEquals(Reason.NO_CANDIDATE, reasonOf(d))
    }

    // ---- rule 1: same amount ----

    @Test
    fun `rule 1 - a different amount is never the same payment`() {
        val d = CrossChannelMerger.decide(gpay(), earlier(amountMinor = 10_000))
        assertEquals(Reason.DIFFERENT_AMOUNT, reasonOf(d))
    }

    // ---- rule 2: different channels ----

    @Test
    fun `rule 2 - two events from the same channel never merge`() {
        val d = CrossChannelMerger.decide(gpay(), earlier(captureSource = "GPAY_NOTIFICATION"))
        assertEquals(Reason.SAME_CHANNEL, reasonOf(d))
    }

    // ---- rule 3: within the window ----

    @Test
    fun `rule 3 - inside the window merges`() {
        val inside = now - CrossChannelMerger.CROSS_CHANNEL_MERGE_WINDOW_MS + 1
        val d = CrossChannelMerger.decide(gpay(now), earlier(atMs = inside))
        assertTrue("expected a merge, got $d", d is Decision.Merge)
    }

    @Test
    fun `rule 3 - at the window boundary exactly, still merges`() {
        val d = CrossChannelMerger.decide(
            gpay(now),
            earlier(atMs = now - CrossChannelMerger.CROSS_CHANNEL_MERGE_WINDOW_MS),
        )
        assertTrue("expected a merge at exactly the window, got $d", d is Decision.Merge)
    }

    @Test
    fun `rule 3 - one millisecond past the window announces`() {
        val d = CrossChannelMerger.decide(
            gpay(now),
            earlier(atMs = now - CrossChannelMerger.CROSS_CHANNEL_MERGE_WINDOW_MS - 1),
        )
        assertEquals(Reason.OUTSIDE_WINDOW, reasonOf(d))
    }

    @Test
    fun `rule 3 - the window is 90 seconds, not five minutes`() {
        // The regression this whole change exists to prevent: the old 5-minute
        // bucket swallowed genuinely separate same-amount payments.
        assertEquals(90_000L, CrossChannelMerger.CROSS_CHANNEL_MERGE_WINDOW_MS)
    }

    // ---- rule 4: no reference on either side ----

    @Test
    fun `rule 4 - a reference on the incoming side blocks the merge`() {
        val d = CrossChannelMerger.decide(gpay(ref = true), earlier())
        assertEquals(Reason.REFERENCE_PRESENT, reasonOf(d))
    }

    @Test
    fun `rule 4 - a reference on the earlier side blocks the merge too`() {
        val d = CrossChannelMerger.decide(gpay(), earlier(ref = true))
        assertEquals(Reason.REFERENCE_PRESENT, reasonOf(d))
    }

    // ---- the merge itself ----

    @Test
    fun `a merge names the event that was already announced`() {
        val d = CrossChannelMerger.decide(gpay(), earlier(eventId = "evt_first"))
        assertEquals("evt_first", (d as Decision.Merge).earlier.eventId)
    }

    @Test
    fun `every decision has a countable trace token`() {
        assertEquals("merge-cross-channel", CrossChannelMerger.reasonToken(
            CrossChannelMerger.decide(gpay(), earlier()),
        ))
        assertEquals("merge-keep-same-channel", CrossChannelMerger.reasonToken(
            CrossChannelMerger.decide(gpay(), earlier(captureSource = "GPAY_NOTIFICATION")),
        ))
        assertEquals("merge-keep-outside-window", CrossChannelMerger.reasonToken(
            CrossChannelMerger.decide(
                gpay(now),
                earlier(atMs = now - 10 * 60_000),
            ),
        ))
    }

    // ---- the invariant underneath all of it ----

    @Test
    fun `every combination that is not an all-rules-pass announces`() {
        val channels = listOf("GPAY_NOTIFICATION", "SMS_KOTAK", "SMS_BANK")
        val amounts = listOf(50_000L, 10_000L)
        var merges = 0
        var checks = 0
        for (c1 in channels) for (c2 in channels) for (a1 in amounts) for (a2 in amounts) {
            for (dt in longArrayOf(0, 45_000, 120_000)) for (r1 in listOf(false, true)) {
                for (r2 in listOf(false, true)) {
                    val incoming = Incoming(a1, now, c1, r1)
                    val prior = Earlier("evt_p", a2, now - dt, c2, r2)
                    val d = CrossChannelMerger.decide(incoming, prior)
                    checks++
                    val shouldMerge = a1 == a2 && c1 != c2 && dt <= 90_000L &&
                        !r1 && !r2
                    if (d is Decision.Merge) {
                        merges++
                        assertTrue("merged a pair that should not merge: $incoming vs $prior", shouldMerge)
                    } else {
                        assertTrue(
                            "failed to announce a pair that should not merge: $incoming vs $prior",
                            !shouldMerge,
                        )
                    }
                }
            }
        }
        assertEquals(3 * 3 * 2 * 2 * 3 * 2 * 2, checks)
        assertTrue("no merge was ever produced, the rules are dead", merges > 0)
    }
}

package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.parser.NotificationTextResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reliability fix 2: the extras fallback chain. A GPay payment whose amount
 * appears only in EXTRA_BIG_TEXT (or EXTRA_SUB_TEXT) must still be parsed —
 * the candidate order must guarantee every non-blank extra gets a chance.
 */
class NotificationTextResolverTest {

    @Test
    fun `bigText is tried before text`() {
        val candidates = NotificationTextResolver.candidates(
            title = "Payment",
            text = "₹500 received",
            bigText = "You received ₹500.00 from Rahul via Google Pay",
            subText = null,
        )
        assertEquals(2, candidates.size)
        assertEquals("You received ₹500.00 from Rahul via Google Pay", candidates[0].second)
        assertEquals("₹500 received", candidates[1].second)
        // Title is carried on every candidate.
        assertTrue(candidates.all { it.first == "Payment" })
    }

    @Test
    fun `amount only in subText is still a candidate`() {
        val candidates = NotificationTextResolver.candidates(
            title = "Google Pay",
            text = null,
            bigText = null,
            subText = "₹250.50 sent to you",
        )
        assertEquals(1, candidates.size)
        assertEquals("₹250.50 sent to you", candidates[0].second)
    }

    @Test
    fun `blank and null extras are skipped`() {
        val candidates = NotificationTextResolver.candidates(
            title = "t",
            text = "   ",
            bigText = null,
            subText = "",
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `null title is preserved on candidates`() {
        val candidates = NotificationTextResolver.candidates(
            title = null,
            text = "₹500 received",
            bigText = null,
            subText = null,
        )
        assertEquals(1, candidates.size)
        assertEquals(null, candidates[0].first)
        assertEquals("₹500 received", candidates[0].second)
    }

    @Test
    fun `every non-blank extra appears exactly once`() {
        val candidates = NotificationTextResolver.candidates(
            title = "t",
            text = "a",
            bigText = "b",
            subText = "c",
        )
        assertEquals(listOf("b", "a", "c"), candidates.map { it.second })
        assertFalse(candidates.isEmpty())
    }
}

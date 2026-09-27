package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.parser.DirectionClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionClassifierTest {

    @Test
    fun `received phrases classify RECEIVED HIGH`() {
        listOf(
            "₹500 received",
            "Payment received",
            "₹500 credited to your account",
            "money received from Rahul",
            "amount credited by UPI",
        ).forEach {
            val (d, c) = DirectionClassifier.classify(it)
            assertEquals("text: $it", Direction.RECEIVED, d)
            assertEquals("text: $it", Confidence.HIGH, c)
        }
    }

    @Test
    fun `sent phrases classify SENT`() {
        listOf(
            "₹500 paid",
            "You sent ₹500 to Shop",
            "₹1,000 debited from your account",
            "payment made to Amit",
            "paid to Kirana store",
        ).forEach {
            val (d, c) = DirectionClassifier.classify(it)
            assertEquals("text: $it", Direction.SENT, d)
        }
    }

    @Test
    fun `failed transaction never announces`() {
        val (d, c) = DirectionClassifier.classify("Payment of ₹500 to X failed. Amount received back to source later.")
        assertEquals(Direction.UNKNOWN, d)
        assertEquals(Confidence.LOW, c)
    }

    @Test
    fun `request phrase suppressed`() {
        val (d, _) = DirectionClassifier.classify("Rahul sent you a request for ₹500")
        assertEquals(Direction.UNKNOWN, d)
    }

    @Test
    fun `mixed leaning received stays received`() {
        // "paid" appears but "credited" dominates.
        val (d, c) = DirectionClassifier.classify("₹500 credited by Rahul. You paid nothing.")
        assertEquals(Direction.RECEIVED, d)
        assertEquals(Confidence.MEDIUM, c)
    }

    @Test
    fun `neutral text is unknown low`() {
        val (d, c) = DirectionClassifier.classify("Welcome to Google Pay!")
        assertEquals(Direction.UNKNOWN, d)
        assertEquals(Confidence.LOW, c)
    }

    @Test
    fun `normalization collapses whitespace and case`() {
        assertEquals("a b c", DirectionClassifier.normalize("  A   B\t C "))
    }
}

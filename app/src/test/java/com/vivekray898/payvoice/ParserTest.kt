package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.CaptureSource
import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.GooglePayParser
import com.vivekray898.payvoice.core.parser.PaymentParserRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    private val gpay = GooglePayParser()

    // ---- Google Pay ----

    @Test
    fun `gpay received with sender is announceable high`() {
        val p = gpay.parse("Payment received", "₹500 received from Rahul Sharma. Upi Ref 512345678901")
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(Confidence.HIGH, p.confidence)
        assertEquals(50000L, p.amountMinor)
        assertEquals("Rahul Sharma", p.senderName)
        assertTrue(p.isAnnounceable)
    }

    @Test
    fun `gpay received inverted wording`() {
        val p = gpay.parse("You received ₹1,200.50 from Amit", null)
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(120050L, p.amountMinor)
        assertEquals("Amit", p.senderName)
    }

    @Test
    fun `gpay sent is not announceable`() {
        val p = gpay.parse("Payment sent", "₹500 paid to Kirana Store")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(false, p.isAnnounceable)
    }

    @Test
    fun `gpay failed payment is rejected`() {
        val p = gpay.parse("Payment failed", "₹500 payment to X failed")
        assertNull(p)
    }

    @Test
    fun `gpay request money is rejected`() {
        val p = gpay.parse("Request", "Rahul sent you a request for ₹500")
        assertNull(p)
    }

    @Test
    fun `gpay promotional is rejected`() {
        val p = gpay.parse("Offer", "Scratch card unlocked! Deal ends soon")
        assertNull(p)
    }

    @Test
    fun `gpay unrelated non-payment returns null`() {
        assertNull(gpay.parse("Google Pay", "Your monthly summary is ready"))
    }

    @Test
    fun `gpay credit without amount still surfaces with direction`() {
        val p = gpay.parse("Money received", "You have money received from a friend")
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertNull(p.amountMinor)
        assertEquals(false, p.isAnnounceable)
    }

    // ---- Source architecture guards (Kotak app-notification cleanup) ----

    @Test
    fun `gpay package routes to source`() {
        assertEquals(
            PaymentSource.GOOGLE_PAY,
            PaymentSource.fromPackage("com.google.android.apps.nbu.paisa.user"),
        )
    }

    @Test
    fun `kotak app package is NEVER a payment notification source`() {
        // Bank payments arrive via SMS only. A Kotak banking-app notification
        // must match no source, no parser, and therefore create no event.
        assertNull(PaymentSource.fromPackage("com.kotak811"))
        assertNull(
            PaymentParserRegistry.withDefaults().parserForPackage("com.kotak811"),
        )
    }

    @Test
    fun `arbitrary bank app packages are never sources`() {
        assertNull(PaymentSource.fromPackage("com.hdfcbank.app"))
        assertNull(PaymentSource.fromPackage("net.one97.paytm"))
        assertNull(PaymentSource.fromPackage("com.phonepe.app"))
    }

    @Test
    fun `capture sources are exactly the three supported channels`() {
        // GPAY_NOTIFICATION + SMS_KOTAK + SMS_BANK are the payment channels;
        // OTHER_NOTIFICATION exists only as a local diagnostics catch-all.
        assertEquals(
            setOf("GPAY_NOTIFICATION", "SMS_KOTAK", "SMS_BANK", "OTHER_NOTIFICATION"),
            CaptureSource.entries.map { it.name }.toSet(),
        )
        assertTrue(!CaptureSource.entries.any { it.name == "KOTAK_NOTIFICATION" })
    }
}

package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.GooglePayParser
import com.vivekray898.payvoice.core.parser.KotakParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    private val gpay = GooglePayParser()
    private val kotak = KotakParser()

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

    // ---- Kotak (provisional templates) ----

    @Test
    fun `kotak credit is announceable`() {
        val p = kotak.parse("Kotak Bank", "Rs. 1200 credited to your account from RAMESH K Ref no 880123456")
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(120000L, p.amountMinor)
        assertEquals("RAMESH K", p.senderName)
        assertNotNull(p.referenceId)
        assertTrue(p.isAnnounceable)
    }

    @Test
    fun `kotak debit is not announceable`() {
        val p = kotak.parse("Kotak Bank", "Rs. 300 debited from your account towards ATM withdrawal")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(false, p.isAnnounceable)
    }

    @Test
    fun `kotak OTP is rejected`() {
        assertNull(kotak.parse("Kotak", "Your OTP is 123456. Do not share"))
    }

    @Test
    fun `sources route by verified package`() {
        assertEquals(
            PaymentSource.GOOGLE_PAY,
            PaymentSource.fromPackage("com.google.android.apps.nbu.paisa.user"),
        )
        // Placeholder must not match anything at runtime.
        assertNull(PaymentSource.fromPackage("com.kotak.app.unverified.placeholder"))
    }
}

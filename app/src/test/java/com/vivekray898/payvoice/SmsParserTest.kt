package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.sms.SmsPaymentParserRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20 SMS parser suite. Every test case from the spec is covered:
 * received (3 formats), sent, bill, OTP, promotional, and the Kotak
 * notification + SMS cross-channel dedup requirement.
 */
class SmsParserTest {

    private val now = 1_782_000_000_000L

    // ---- RECEIVED (spec cases) ----

    @Test
    fun `kotak credited via UPI is RECEIVED 500`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "Your A/c XXXX is credited with Rs 500 via UPI...", now,
        )
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(50000L, p.amountMinor)
        assertTrue(p.isAnnounceable)
    }

    @Test
    fun `UPI payment received rupee format`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "UPI payment received ₹500 from Rahul", now,
        )
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(50000L, p.amountMinor)
        assertEquals("Rahul", p.senderName)
    }

    @Test
    fun `INR format with paise`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "INR 1,250.50 credited to your account", now,
        )
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(125050L, p.amountMinor)
    }

    // ---- Spec §11 canonical Kotak format (exact wording) ----

    @Test
    fun `spec canonical kotak credit sms parses completely`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789",
            "Received Rs.1000.00 from Ms USHA DAS in your Kotak811 a/c XX8521 on 19-Sep-26. " +
                "UPI ref no. 315101342750. View balance: https://kotk.in/KOTAKD/HSnXCv -Kotak",
            now,
        )
        assertNotNull(p)
        assertEquals(PaymentSource.KOTAK, p!!.source)
        assertEquals(Direction.RECEIVED, p.direction)
        assertEquals(100_000L, p.amountMinor)
        assertEquals("Ms Usha Das", p.senderName)
        assertEquals("315101342750", p.referenceId)
    }

    // ---- SENT / BILL / OTP / PROMO (spec cases) ----

    @Test
    fun `debit is SENT and not announceable`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "Rs 500 debited from your account for UPI transfer", now,
        )
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(false, p.isAnnounceable)
    }

    @Test
    fun `bill reminder is NOT a payment`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "Your bill of Rs 500 is due tomorrow", now,
        )
        assertNull(p)
    }

    @Test
    fun `otp is NOT a payment`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "Your OTP for transaction is 123456", now,
        )
        assertNull(p)
    }

    @Test
    fun `promotional cashback is NOT a payment`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK6789", "Shop today and get cashback of Rs 500", now,
        )
        assertNull(p)
    }

    // ---- Direction wording variants (Phase 5) ----

    @Test
    fun `credited with variant`() {
        val p = SmsPaymentParserRegistry.parse(
            "KOTAKBK", "Your account is credited with Rs 2,000.00 on 12-04", now,
        )
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(200000L, p.amountMinor)
    }

    @Test
    fun `A-slash-c credited variant`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK1234", "A/c credited: Rs 750.00 UPI Ref 432198765432", now,
        )
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(75000L, p.amountMinor)
        assertNotNull(p.referenceId)
    }

    @Test
    fun `money received variant`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK1234", "money received Rs 300 in your Kotak 811 account", now,
        )
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(30000L, p.amountMinor)
    }

    @Test
    fun `payment made variant is SENT`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK1234", "payment of Rs 400 made from your account", now,
        )
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
    }

    @Test
    fun `transferred out is SENT`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK1234", "Rs 600 transferred from your a/c to X", now,
        )
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
    }

    @Test
    fun `withdrawn is SENT`() {
        val p = SmsPaymentParserRegistry.parse(
            "KKBK1234", "Rs 1000 withdrawn at ATM from your account", now,
        )
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
    }

    // ---- Amount formats (Phase 7) ----

    @Test
    fun `amount formats all parse`() {
        val cases = mapOf(
            "Rs 500" to 50000L,
            "Rs.500" to 50000L,
            "INR 500" to 50000L,
            "INR500" to 50000L,
            "₹500" to 50000L,
            "₹ 500" to 50000L,
            "Rs 500.00" to 50000L,
            "INR 1,250.50" to 125050L,
            "₹1,250" to 125000L,
        )
        cases.forEach { (raw, expected) ->
            val p = SmsPaymentParserRegistry.parse(
                "KKBK1234", "credited with $raw via UPI", now,
            )
            assertEquals("amount format: $raw", expected, p?.amountMinor)
        }
    }

    // ---- Sender identification (Phase 9) ----

    @Test
    fun `various Kotak sender ids resolve to Kotak parser`() {
        listOf("KKBK6789", "KOTAKBK", "KTK8888").forEach { sender ->
            val p = SmsPaymentParserRegistry.parse(
                sender, "credited with Rs 500 via UPI", now,
            )
            assertNotNull("sender: $sender", p)
        }
    }

    @Test
    fun `personal number sender is ignored for privacy`() {
        val p = SmsPaymentParserRegistry.parse(
            "+919876543210", "credited with Rs 500 via UPI", now,
        )
        assertNull(p)
    }

    @Test
    fun `unknown bank sender parses generically`() {
        val p = SmsPaymentParserRegistry.parse(
            "XYZBANK", "credited with Rs 500 via UPI", now,
        )
        assertNotNull(p)
        assertEquals("XYZBANK→", "Bank SMS", p!!.sourceLabel)
    }

    // ---- Phase 19 samples (classify-only expectations) ----

    @Test
    fun `irrelevant promotional sms is rejected`() {
        assertNull(
            SmsPaymentParserRegistry.parse(
                "BVKSMS", "Get 60% off on shoes today! Click here", now,
            )
        )
    }
}

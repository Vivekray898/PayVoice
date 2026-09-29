package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.parser.sms.SmsPaymentParserRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reliability fix 4: real Indian bank SMS formats must all classify. Each
 * case here is a format banks actually send; a regression in any of them
 * means an offline payment is silently dropped (GPay posts nothing when the
 * device is offline — the SMS is the ONLY evidence of the payment).
 */
class BankSmsFormatTest {

    private val now = 1_782_000_000_000L

    private fun parse(sender: String, body: String) =
        SmsPaymentParserRegistry.parse(sender, body, now)

    // ---- HDFC / ICICI style ----

    @Test
    fun `hdfc-style debit - Rs-500-00 debited from A-c XX1234`() {
        val p = parse("VM-HDFCBK", "Rs.500.00 debited from A/c XX1234 on 29-09-26 for UPI. Not you? Call 18001234")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(50000L, p.amountMinor)
    }

    // ---- SBI style ----

    @Test
    fun `sbi-style spend - INR 500-00 spent`() {
        val p = parse("AX-SBIINB", "INR 500.00 spent on your A/c XX4321 on 29-09-26 by UPI")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(50000L, p.amountMinor)
    }

    // ---- Kotak / Axis style ----

    @Test
    fun `kotak-style debit - Rs 500 debited`() {
        val p = parse("KKBK6789", "Rs 500 debited from your a/c XX8521 on 29-09-26 for UPI transaction")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(50000L, p.amountMinor)
    }

    // ---- GPay-linked bank SMS (sent flow) ----

    @Test
    fun `gpay-linked sent - Sent Rs-500-00 from account`() {
        val p = parse("KKBK6789", "Sent Rs.500.00 from your Kotak 811 a/c XX8521 to merchant. UPI ref 315101342750")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(50000L, p.amountMinor)
    }

    // ---- Debit-by construction ----

    @Test
    fun `debit-by construction - A-c XX1234 debited by Rs-500-00`() {
        val p = parse("AD-ICICIB", "A/c XX1234 debited by Rs.500.00 on 29-09-26")
        assertNotNull(p)
        assertEquals(Direction.SENT, p!!.direction)
        assertEquals(50000L, p.amountMinor)
    }

    // ---- RECEIVED side of the same formats (offline credit) ----

    @Test
    fun `hdfc-style credit announces`() {
        val p = parse("VM-HDFCBK", "Rs.1,250.00 credited to A/c XX1234 on 29-09-26 by UPI ref 432198765432")
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(125000L, p.amountMinor)
        assertEquals("432198765432", p.referenceId)
    }

    @Test
    fun `sbi-style credit announces`() {
        val p = parse("AX-SBIINB", "INR 750.00 credited to your A/c XX4321 by UPI")
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(75000L, p.amountMinor)
    }

    @Test
    fun `icici-style UPI credit announces`() {
        val p = parse("AD-ICICIB", "Rs 300 credited by UPI from RAHUL KUMAR to your a/c XX1234")
        assertNotNull(p)
        assertEquals(Direction.RECEIVED, p!!.direction)
        assertEquals(30000L, p.amountMinor)
    }

    // ---- NON-payment guards on the same senders ----

    @Test
    fun `otp from a bank sender is never a payment`() {
        assertNull(parse("VM-HDFCBK", "Use OTP 123456 to complete your transaction. Never share it."))
    }

    @Test
    fun `balance-inquiry sms is never announced`() {
        val p = parse("AX-SBIINB", "Your available balance is Rs 8,500.00 as on 29-09-26")
        assertNull(p)
    }
}

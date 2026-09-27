package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.parser.Fingerprinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Phase 12/20: cross-channel deduplication. The SAME payment arriving as a
 * Kotak notification AND a Kotak SMS must produce ONE fingerprint (one
 * announcement, one Room row). Two separate payments must never merge.
 */
class CrossChannelDedupTest {

    private val now = 1_782_000_000_000L

    @Test
    fun `same UTR via notification and SMS collides`() {
        val notif = Fingerprinter.captureFingerprint(
            packageId = "com.kotak811",
            amountMinor = 50000,
            timestampMs = now,
            title = "Kotak",
            text = "Rs 500 credited UPI Ref 432198765432",
            referenceId = "432198765432",
            captureSource = "KOTAK_NOTIFICATION",
        )
        val sms = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789",
            amountMinor = 50000,
            timestampMs = now + 20_000,
            title = null,
            text = "credited with Rs 500 UPI Ref 432198765432",
            referenceId = "432198765432",
            captureSource = "SMS_KOTAK",
        )
        assertEquals(notif, sms)
    }

    @Test
    fun `different UTR same amount stays distinct`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "x", referenceId = "432198765432",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "x", referenceId = "432198765433",
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `no reference - same amount and sender within window collides`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", senderName = "Rahul Sharma",
            captureSource = "KOTAK_NOTIFICATION",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now + 40_000,
            title = null, text = "credited", senderName = "rahul sharma",
            captureSource = "SMS_KOTAK",
        )
        assertEquals(a, b)
    }

    @Test
    fun `no reference - different senders never merge`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", senderName = "Rahul Sharma",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", senderName = "Amit Verma",
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `no reference - outside the 5 minute window stays distinct`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", senderName = "Rahul",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now + 6 * 60_000,
            title = null, text = "credited", senderName = "Rahul",
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `different amounts never merge`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 10000, timestampMs = now,
            title = null, text = "credited",
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `channel tags do not affect fingerprints`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = "x", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", referenceId = "REF1",
            captureSource = "GPAY_NOTIFICATION",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "y", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", referenceId = "REF1",
            captureSource = "SMS_BANK",
        )
        assertEquals(a, b)
    }
}

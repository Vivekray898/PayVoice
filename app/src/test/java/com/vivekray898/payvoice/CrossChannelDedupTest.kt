package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.KnownPackages
import com.vivekray898.payvoice.core.parser.Fingerprinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Phase 12/20: cross-channel deduplication. The SAME payment arriving via two
 * channels (e.g. a GPay notification and a bank SMS for the same UTR) must
 * produce ONE fingerprint (one announcement, one Room row). Two separate
 * payments must never merge.
 */
class CrossChannelDedupTest {

    private val now = 1_782_000_000_000L

    @Test
    fun `same UTR via notification and SMS collides`() {
        val notif = Fingerprinter.captureFingerprint(
            packageId = KnownPackages.GOOGLE_PAY,
            amountMinor = 50000,
            timestampMs = now,
            title = "Payment received",
            text = "Rs 500 credited UPI Ref 432198765432",
            referenceId = "432198765432",
            captureSource = "GPAY_NOTIFICATION",
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
    fun `no reference - same amount and sender NEVER collide on the key alone`() {
        // The old key was AMT|<amount>|<sender>|<postTime/5min>, so these two
        // collapsed into one row and the second payment was lost. Reconciling
        // two channels for one payment is now CrossChannelMerger's job, and it
        // applies a 90s window with an explicit same-channel guard — not a
        // side effect of hashing a wall clock.
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited", senderName = "Rahul Sharma",
            captureSource = "GPAY_NOTIFICATION",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now + 40_000,
            title = null, text = "credited", senderName = "rahul sharma",
            captureSource = "SMS_KOTAK",
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `no reference - exact same source event still collapses`() {
        // The guarantee the old bucket gave, kept for the case that actually
        // needs it: one system event delivered twice is one payment.
        val sbnId = 7
        val sbnTag = "txn"
        fun fp() = Fingerprinter.captureFingerprint(
            packageId = "com.google.android.apps.nbu.paisa.user",
            amountMinor = 50000, timestampMs = now,
            title = "Payment received", text = "₹500 received from Rahul",
            notificationId = sbnId, notificationTag = sbnTag,
        )
        assertEquals(fp(), fp())
    }

    @Test
    fun `SRC tier - different post time is a different payment`() {
        val a = Fingerprinter.captureFingerprint(
            packageId = KnownPackages.GOOGLE_PAY, amountMinor = 50000, timestampMs = now,
            title = "Payment received", text = "₹500 received from Rahul",
            notificationId = 42, notificationTag = null,
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = KnownPackages.GOOGLE_PAY, amountMinor = 50000, timestampMs = now + 59_000,
            title = "Payment received", text = "₹500 received from Rahul",
            notificationId = 42, notificationTag = null,
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `TXT tier - SMS with no identity keys on exact time, not a bucket`() {
        fun fp(t: Long) = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = t,
            title = null, text = "credited Rs 500",
        )
        assertEquals(fp(now), fp(now))
        assertNotEquals(fp(now), fp(now + 1))
    }

    @Test
    fun `no reference - different senders never merge`() {
        // Real reference-less SMS text always names the sender, so the TXT tier
        // separates them through the content it actually receives. (The key no
        // longer takes senderName as an input: a synthetic body of "credited"
        // with no name in it is not something a bank sends.)
        val a = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited Rs 500 from Rahul Sharma", senderName = "Rahul Sharma",
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now,
            title = null, text = "credited Rs 500 from Amit Verma", senderName = "Amit Verma",
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
    fun `reference beats the source identity`() {
        // A reference is authoritative even when a stable sbn identity exists.
        val a = Fingerprinter.captureFingerprint(
            packageId = KnownPackages.GOOGLE_PAY, amountMinor = 50000, timestampMs = now,
            title = "t", text = "x", referenceId = "utr1", notificationId = 1,
        )
        val b = Fingerprinter.captureFingerprint(
            packageId = "KKBK6789", amountMinor = 50000, timestampMs = now + 9_000,
            title = null, text = "y", referenceId = "utr1",
        )
        assertEquals(a, b)
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

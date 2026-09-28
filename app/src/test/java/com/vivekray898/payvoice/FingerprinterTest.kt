package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.parser.Fingerprinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprinterTest {

    @Test
    fun `same inputs produce identical fingerprint`() {
        val a = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_000_000, "T", "₹500 received")
        val b = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_000_000, "T", "₹500 received")
        assertEquals(a, b)
    }

    @Test
    fun `repost within same minute collapses`() {
        // Base sits 30s after a bucket boundary so both instants share a bucket.
        val a = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_030_000, "T", "₹500 received")
        val b = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_030_000 + 29_000, "T", "₹500 received")
        assertEquals(a, b)
    }

    @Test
    fun `repost in later minute is distinct`() {
        val a = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_030_000, "T", "₹500 received")
        val b = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_030_000 + 61_000, "T", "₹500 received")
        assertNotEquals(a, b)
    }

    @Test
    fun `different amounts differ`() {
        val a = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_000_000, "T", "₹500 received")
        val b = Fingerprinter.fingerprint("pkg", 10000L, 1_780_000_000_000, "T", "₹100 received")
        assertNotEquals(a, b)
    }

    @Test
    fun `case and whitespace of text do not matter`() {
        val a = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_000_000, "Payment", "₹500  RECEIVED")
        val b = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_000_000, "payment", "₹500 received")
        assertEquals(a, b)
    }

    @Test
    fun `id is prefixed and hex`() {
        val a = Fingerprinter.fingerprint("pkg", 50000L, 1_780_000_000_000, "T", "x")
        assertTrue(a.startsWith("evt_"))
        assertEquals(64, a.removePrefix("evt_").length)
        assertTrue(a.removePrefix("evt_").all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `cross-channel id fits every layer cap (rls 64, gateway 64, validator 64)`() {
        // RLS insert policy on payment_events accepts length(id) in 8..64;
        // the gateway parseEvent and RemoteEventValidator enforce the same.
        // captureFingerprint MUST stay within it or every real fan-out dies.
        val a = Fingerprinter.captureFingerprint(
            packageId = "com.google.android.apps.nbu.paisa.user",
            amountMinor = 100L,
            timestampMs = 1_780_000_000_000,
            title = "Payment received",
            text = "₹1 received from Test. Upi Ref 512345678901",
            referenceId = "512345678901",
        )
        assertTrue(a.length in 8..64)
        assertTrue(a.startsWith("evt_"))
    }

    private fun assertTrue(b: Boolean) {
        org.junit.Assert.assertTrue(b)
    }
}

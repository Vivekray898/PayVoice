package com.vivekray898.payvoice.core.parser.sms

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Presentation-only name normalization (spec §12): honorifics, initials, and
 * dotted-initial forms. Never identity inference — casing/spacing only.
 */
class SmsNameNormalizerTest {

    // ---- Spec §12 canonical cases ----

    @Test
    fun `honorific uppercase normalizes`() {
        assertEquals("Ms Usha Das", SmsNameNormalizer.normalize("Ms USHA DAS"))
    }

    @Test
    fun `mr honorific normalizes`() {
        assertEquals("Mr Rahul Sharma", SmsNameNormalizer.normalize("MR RAHUL SHARMA"))
    }

    @Test
    fun `bare initials stay spaced and capitalized`() {
        assertEquals("S K Sharma", SmsNameNormalizer.normalize("S K SHARMA"))
    }

    @Test
    fun `dotted initials keep dots`() {
        assertEquals("A. K. Gupta", SmsNameNormalizer.normalize("A. K. GUPTA"))
    }

    @Test
    fun `already-normalized names are stable`() {
        assertEquals("Rahul Sharma", SmsNameNormalizer.normalize("Rahul Sharma"))
    }

    @Test
    fun `extra whitespace collapses`() {
        assertEquals("Ms Usha Das", SmsNameNormalizer.normalize("  Ms   USHA   DAS  "))
    }

    @Test
    fun `mixed case names capitalize each word`() {
        assertEquals("Rahul Kumar Sharma", SmsNameNormalizer.normalize("rahul kumar SHARMA"))
    }

    // ---- UPI handles ----

    @Test
    fun `upi handle lowercased`() {
        assertEquals("rahul@ybl", SmsNameNormalizer.normalize("Rahul@YBL"))
    }

    // ---- Invalid input ----

    @Test
    fun `null gives null`() {
        assertEquals(null, SmsNameNormalizer.normalize(null))
    }

    @Test
    fun `blank gives null`() {
        assertEquals(null, SmsNameNormalizer.normalize("   "))
    }

    @Test
    fun `too-long strings are rejected`() {
        assertEquals(null, SmsNameNormalizer.normalize("A Very Long Name That Exceeds All Reasonable Presentation Limits"))
    }
}

package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.parser.AmountExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountExtractorTest {

    @Test
    fun `rupee symbol plain amount`() {
        assertEquals(50000L, AmountExtractor.extract("₹500 received")?.minorUnits)
    }

    @Test
    fun `rupee symbol with decimals`() {
        assertEquals(50050L, AmountExtractor.extract("₹500.50 received")?.minorUnits)
    }

    @Test
    fun `indian grouping lakh`() {
        assertEquals(120000075L, AmountExtractor.extract("Rs. 12,00,000.75 credited")?.minorUnits)
    }

    @Test
    fun `indian grouping one lakh twenty thousand`() {
        assertEquals(12000000L, AmountExtractor.extract("Rs 1,20,000 debited")?.minorUnits)
    }

    @Test
    fun `INR prefix`() {
        assertEquals(25000L, AmountExtractor.extract("INR 250 received from X")?.minorUnits)
    }

    @Test
    fun `Rs dot no space`() {
        assertEquals(10000L, AmountExtractor.extract("Rs.100 sent")?.minorUnits)
    }

    @Test
    fun `prefer largest picks transaction over balance`() {
        val text = "₹500 received. Balance: ₹45,000"
        assertEquals(4500000L, AmountExtractor.extract(text, preferLargest = true)?.minorUnits)
        assertEquals(50000L, AmountExtractor.extract(text, preferLargest = false)?.minorUnits)
    }

    @Test
    fun `no amount returns null`() {
        assertNull(AmountExtractor.extract("Payment successful"))
    }

    @Test
    fun `zero amount rejected`() {
        assertNull(AmountExtractor.extract("₹0 received"))
    }

    @Test
    fun `toMinorUnits rejects garbage`() {
        assertNull(AmountExtractor.toMinorUnits("abc"))
        assertNull(AmountExtractor.toMinorUnits(""))
        assertNull(AmountExtractor.toMinorUnits("1.234"))
    }

    @Test
    fun `formatting drops trailing paise`() {
        assertEquals("₹500", AmountExtractor.formatMinor(50000))
        assertEquals("₹500.50", AmountExtractor.formatMinor(50050))
        assertEquals("₹1,20,000".replace(",", ","), AmountExtractor.formatMinor(12000000))
    }
}

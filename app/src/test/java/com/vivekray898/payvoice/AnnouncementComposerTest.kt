package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.announce.AmountToWords
import com.vivekray898.payvoice.core.model.PaymentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementComposerTest {

    @Test
    fun `english cardinal basics`() {
        assertEquals("zero", AmountToWords.englishCardinal(0))
        assertEquals("five", AmountToWords.englishCardinal(5))
        assertEquals("twenty-one", AmountToWords.englishCardinal(21))
        assertEquals("one hundred", AmountToWords.englishCardinal(100))
        assertEquals("five hundred", AmountToWords.englishCardinal(500))
        assertEquals("one thousand two hundred", AmountToWords.englishCardinal(1200))
        assertEquals("fifty thousand", AmountToWords.englishCardinal(50_000))
        assertEquals("one lakh twenty thousand", AmountToWords.englishCardinal(120_000))
        assertEquals("one crore", AmountToWords.englishCardinal(10_000_000))
    }

    @Test
    fun `english amount words with paise`() {
        assertEquals(
            "five hundred rupees and fifty paise",
            AmountToWords.englishWords(50050),
        )
        assertEquals("five hundred rupees", AmountToWords.englishWords(50000))
    }

    @Test
    fun `hindi amount words`() {
        assertTrue(AmountToWords.hindiWords(50000).contains("पाँच सौ"))
        assertTrue(AmountToWords.hindiWords(50000).contains("रुपये"))
    }

    @Test
    fun `compose amount only english`() {
        val text = AnnouncementComposer.compose(
            50000, "Rahul", PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_ONLY, AnnouncementLanguage.ENGLISH,
        )
        assertEquals("Five hundred rupees received.", text)
    }

    @Test
    fun `compose amount sender english`() {
        val text = AnnouncementComposer.compose(
            50000, "Rahul", PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.ENGLISH,
        )
        assertEquals("Five hundred rupees received from Rahul.", text)
    }

    @Test
    fun `compose full english includes source`() {
        val text = AnnouncementComposer.compose(
            50000, "Rahul", PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.FULL, AnnouncementLanguage.ENGLISH,
        )
        assertTrue(text.contains("Five hundred rupees"))
        assertTrue(text.contains("Rahul"))
        assertTrue(text.contains("Google Pay"))
    }

    @Test
    fun `compose hindi amount sender`() {
        val text = AnnouncementComposer.compose(
            50000, "Rahul", PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.HINDI,
        )
        assertTrue(text.contains("पाँच सौ रुपये"))
        assertTrue(text.contains("Rahul से प्राप्त हुए"))
    }

    @Test
    fun `compose hinglish`() {
        val text = AnnouncementComposer.compose(
            50000, null, PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.HINGLISH,
        )
        assertEquals("Five hundred rupees mil gaye.", text)
    }

    @Test
    fun `compose without sender falls back gracefully`() {
        val text = AnnouncementComposer.compose(
            50000, null, PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.ENGLISH,
        )
        assertEquals("Five hundred rupees received.", text)
    }

    @Test
    fun `test announcement never references amounts`() {
        val en = AnnouncementComposer.TEST_ANNOUNCEMENT_EN
        val hi = AnnouncementComposer.TEST_ANNOUNCEMENT_HI
        assertTrue(en.contains("test"))
        assertTrue(hi.contains("टेस्ट"))
    }
}

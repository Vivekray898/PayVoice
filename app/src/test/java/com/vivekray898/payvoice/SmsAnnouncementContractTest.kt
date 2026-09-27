package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.model.PaymentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Announcement contract (spec §13, §21):
 *  - sender available + trusted → "amount + sender"
 *  - sender unavailable → amount only
 *  - NEVER "unknown user/sender/someone"
 *  - GPay: untrusted sender ⇒ amount only. SMS: parsed sender is trusted.
 */
class SmsAnnouncementContractTest {

    private val forbidden = listOf("unknown user", "unknown sender", "someone", "somebody", "अज्ञात")

    private fun assertNoForbiddenWording(text: String) {
        forbidden.forEach { word ->
            assertFalse("announcement must never contain '$word': \"$text\"", text.contains(word, ignoreCase = true))
        }
    }

    // ---- SMS deterministic format (spec §11/§21) ----

    @Test
    fun `kotak sms with sender`() {
        val text = AnnouncementComposer.composeSms(100_000, "Ms Usha Das")
        assertEquals("Received 1000 rupees from Ms Usha Das.", text)
    }

    @Test
    fun `second spec case mr rahul sharma`() {
        val text = AnnouncementComposer.composeSms(50_000, "Mr Rahul Sharma")
        assertEquals("Received 500 rupees from Mr Rahul Sharma.", text)
    }

    @Test
    fun `paise amounts are spoken`() {
        val text = AnnouncementComposer.composeSms(2_550, "Ms Priya")
        assertEquals("Received 25.50 rupees from Ms Priya.", text)
    }

    @Test
    fun `sms without sender is amount only`() {
        val text = AnnouncementComposer.composeSms(100_000, null)
        assertEquals("Received 1000 rupees.", text)
    }

    @Test
    fun `sms blank sender is amount only`() {
        val text = AnnouncementComposer.composeSms(100_000, "   ")
        assertEquals("Received 1000 rupees.", text)
    }

    // ---- GPay trusted/untrusted sender rule (spec §13) ----

    @Test
    fun `gpay trusted sender announced`() {
        val text = AnnouncementComposer.compose(
            50_000, "Rahul", PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.ENGLISH,
            trustedSender = true,
        )
        assertEquals("Five hundred rupees received from Rahul.", text)
        assertNoForbiddenWording(text)
    }

    @Test
    fun `gpay untrusted sender collapses to amount only`() {
        val text = AnnouncementComposer.compose(
            50_000, "Rahul", PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.ENGLISH,
            trustedSender = false,
        )
        assertEquals("Five hundred rupees received.", text)
        assertNoForbiddenWording(text)
    }

    @Test
    fun `gpay absent sender is amount only`() {
        val text = AnnouncementComposer.compose(
            50_000, null, PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.AMOUNT_SENDER, AnnouncementLanguage.ENGLISH,
            trustedSender = true,
        )
        assertEquals("Five hundred rupees received.", text)
        assertNoForbiddenWording(text)
    }

    // ---- FULL style must never say "unknown sender" (spec §13) ----

    @Test
    fun `full style without sender omits sender clause entirely`() {
        val text = AnnouncementComposer.compose(
            50_000, null, PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.FULL, AnnouncementLanguage.ENGLISH,
        )
        assertFalse(text.contains("unknown", ignoreCase = true))
        assertEquals("Payment received. Five hundred rupees through Google Pay.", text)
    }

    @Test
    fun `full style hindi without sender omits sender clause`() {
        val text = AnnouncementComposer.compose(
            50_000, null, PaymentSource.GOOGLE_PAY,
            AnnouncementStyle.FULL, AnnouncementLanguage.HINDI,
        )
        assertFalse(text.contains("अज्ञात"))
        assertTrue(text.contains("पाँच सौ रुपये"))
    }

    // ---- Cross-cutting: no wording combination ever produces forbidden text ----

    @Test
    fun `no style language or sender combination produces forbidden wording`() {
        val styles = AnnouncementStyle.entries
        val languages = AnnouncementLanguage.entries
        val senders = listOf(null, "", " ", "Rahul")
        val trust = listOf(true, false)
        for (style in styles) {
            for (language in languages) {
                for (sender in senders) {
                    for (trusted in trust) {
                        val text = AnnouncementComposer.compose(
                            50_000, sender, PaymentSource.GOOGLE_PAY, style, language, trusted,
                        )
                        assertNoForbiddenWording(text)
                    }
                }
            }
        }
    }

    @Test
    fun `sms amount-only and sender formats never produce forbidden wording`() {
        val senders = listOf(null, "", "Ms Usha Das", "Rahul@YBL")
        senders.forEach { sender ->
            val text = AnnouncementComposer.composeSms(75_000, sender)
            assertNoForbiddenWording(text)
        }
    }
}

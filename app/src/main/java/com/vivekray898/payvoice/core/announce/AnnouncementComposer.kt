package com.vivekray898.payvoice.core.announce

import com.vivekray898.payvoice.core.model.PaymentSource

/**
 * Composes the final spoken announcement from a payment event plus user
 * settings (spec §11, §25). The composed text travels with the event —
 * Phase 3 puts it inside the FCM payload so children never need a follow-up
 * fetch before speaking.
 *
 * Hard rules (spec §13):
 *  - NEVER "unknown sender/user/someone". No sender → amount-only wording.
 *  - GPay: sender only counts when [trustedSender] is true.
 *  - SMS: parsed sender is trusted by construction (bank-verified channel),
 *    so SMS events route through [composeSms].
 */
object AnnouncementComposer {

    fun compose(
        amountMinor: Long,
        senderName: String?,
        source: PaymentSource,
        style: AnnouncementStyle,
        language: AnnouncementLanguage,
        trustedSender: Boolean = true,
    ): String {
        // GPay rule: untrusted/absent sender collapses to amount-only, which by
        // construction also guarantees the forbidden-wording guarantee.
        val effectiveSender = if (trustedSender) senderName?.trim()?.takeIf { it.isNotEmpty() } else null
        return when (language) {
            AnnouncementLanguage.ENGLISH -> english(amountMinor, effectiveSender, source, style)
            AnnouncementLanguage.HINDI -> hindi(amountMinor, effectiveSender, source, style)
            AnnouncementLanguage.HINGLISH -> hinglish(amountMinor, effectiveSender, style)
        }
    }

    /**
     * SMS announcement format (spec §11/§21): deterministic, numeric —
     * "Received 1000 rupees from Ms Usha Das." / "Received 25.50 rupees from
     * Ms Priya." / "Received 1000 rupees." (no sender). Never account
     * numbers, UPI refs, URLs, dates, balances, or bank names.
     */
    fun composeSms(amountMinor: Long, senderName: String?): String {
        val sender = senderName?.trim()?.takeIf { it.isNotEmpty() }
        val major = amountMinor / 100
        val paise = amountMinor % 100
        val amountText = if (paise == 0L) "$major" else "$major.${paise.toString().padStart(2, '0')}"
        return if (sender != null) {
            "Received $amountText rupees from $sender."
        } else {
            "Received $amountText rupees."
        }
    }

    private fun english(
        amountMinor: Long,
        senderName: String?,
        source: PaymentSource,
        style: AnnouncementStyle,
    ): String {
        val words = AmountToWords.englishWords(amountMinor)
        return when (style) {
            AnnouncementStyle.AMOUNT_ONLY ->
                "${cap(words)} received."
            AnnouncementStyle.AMOUNT_SENDER ->
                if (senderName != null) "${cap(words)} received from $senderName."
                else "${cap(words)} received."
            AnnouncementStyle.FULL ->
                "Payment received. ${cap(words)}" +
                    (senderName?.let { " from $it" } ?: "") +
                    " through ${source.displayName}."
        }
    }

    private fun hindi(
        amountMinor: Long,
        senderName: String?,
        source: PaymentSource,
        style: AnnouncementStyle,
    ): String {
        val words = AmountToWords.hindiWords(amountMinor)
        return when (style) {
            AnnouncementStyle.AMOUNT_ONLY ->
                "$words प्राप्त हुए।"
            AnnouncementStyle.AMOUNT_SENDER ->
                if (senderName != null) "$words $senderName से प्राप्त हुए।"
                else "$words प्राप्त हुए।"
            AnnouncementStyle.FULL ->
                "भुगतान प्राप्त हुआ। $words" +
                    (senderName?.let { ", $it से" } ?: "") +
                    ", ${source.displayName} के माध्यम से।"
        }
    }

    private fun hinglish(
        amountMinor: Long,
        senderName: String?,
        style: AnnouncementStyle,
    ): String {
        // Latin-script code-mix read by the Hindi voice, which handles common
        // Hinglish transliterations on most engines.
        val words = AmountToWords.englishWords(amountMinor)
        return when (style) {
            AnnouncementStyle.AMOUNT_ONLY -> "${cap(words)} mil gaye."
            AnnouncementStyle.AMOUNT_SENDER ->
                if (senderName != null) "${cap(words)} $senderName se mil gaye."
                else "${cap(words)} mil gaye."
            AnnouncementStyle.FULL ->
                "Payment aaya hai. ${cap(words)}${if (senderName != null) " $senderName se" else ""}."
        }
    }

    private fun cap(s: String): String =
        s.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }

    /** Fixed text spoken for owner-triggered test announcements (spec §27). */
    const val TEST_ANNOUNCEMENT_EN = "PayVoice test announcement."
    const val TEST_ANNOUNCEMENT_HI = "PayVoice टेस्ट घोषणा।"
}

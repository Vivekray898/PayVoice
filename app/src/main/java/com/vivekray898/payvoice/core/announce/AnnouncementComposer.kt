package com.vivekray898.payvoice.core.announce

import com.vivekray898.payvoice.core.model.PaymentSource

/**
 * Composes the final spoken announcement from a payment event plus user
 * settings (spec §11, §25). The composed text travels with the event —
 * Phase 3 puts it inside the FCM payload so children never need a follow-up
 * fetch before speaking.
 */
object AnnouncementComposer {

    fun compose(
        amountMinor: Long,
        senderName: String?,
        source: PaymentSource,
        style: AnnouncementStyle,
        language: AnnouncementLanguage,
    ): String = when (language) {
        AnnouncementLanguage.ENGLISH -> english(amountMinor, senderName, source, style)
        AnnouncementLanguage.HINDI -> hindi(amountMinor, senderName, source, style)
        AnnouncementLanguage.HINGLISH -> hinglish(amountMinor, senderName, style)
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
                "Payment received. ${cap(words)} from ${senderName ?: "an unknown sender"}" +
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
                "भुगतान प्राप्त हुआ। $words, ${senderName ?: "अज्ञात"} से, " +
                    "${source.displayName} के माध्यम से।"
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

package com.vivekray898.payvoice.core.parser

import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.ParsedNotification
import com.vivekray898.payvoice.core.model.PaymentSource

/**
 * Parser for the Kotak Bank app.
 *
 * PROVISIONAL: the Kotak package id is unverified (see [PaymentSource.KOTAK])
 * and these templates are based on Kotak's typical bank-style notification
 * wording ("credited to your account", "debited", "Ref no"). Verify against a
 * real captured notification during on-device testing and tighten here.
 */
class KotakParser : PaymentParser {

    override val source = PaymentSource.KOTAK

    override fun parse(title: String?, text: String?): ParsedNotification? {
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val normalized = DirectionClassifier.normalize(combined)

        if (NON_PAYMENT.any { normalized.contains(it) }) return null

        val (direction, confidence) = DirectionClassifier.classify(combined)
        val amount = AmountExtractor.extract(combined, preferLargest = false)
        val amountMinor = amount?.minorUnits
            ?: return if (direction == Direction.UNKNOWN) null
            else parsed(direction, confidence, null, combined, null)

        val sender = extractSender(combined)
        val reference = extractReference(combined)
        return parsed(direction, confidence, amountMinor, combined, sender, reference)
    }

    private fun parsed(
        direction: Direction,
        confidence: Confidence,
        amountMinor: Long?,
        raw: String,
        sender: String?,
        reference: String? = null,
    ) = ParsedNotification(
        source = source,
        direction = direction,
        confidence = confidence,
        amountMinor = amountMinor,
        currency = "INR",
        senderName = sender?.takeIf { it.isNotBlank() },
        referenceId = reference,
        rawTitle = raw.take(RAW_SNIPPET_MAX),
        rawText = "",
    )

    /**
     * Bank wording varies: "...from RAHUL KUMAR", "UPI/Dr/123456/RAHUL",
     * "by UPI from Rahul". Capture up to the next delimiter.
     */
    private fun extractSender(text: String): String? {
        val m = SENDER_REGEX.find(text) ?: return null
        val sender = m.groupValues[1]
            .trim()
            .trimEnd('.', ',', '-', '/', '|')
            .takeIf { it.length in 2..40 }
            ?: return null
        // Bank ids arrive uppercase; keep as-is (announcement reads naturally).
        return sender
    }

    private fun extractReference(text: String): String? =
        REFERENCE_REGEX.find(text)?.groupValues?.get(1)?.trim()?.take(40)

    companion object {
        private const val RAW_SNIPPET_MAX = 160

        private val SENDER_REGEX = Regex(
            "(?i)(?:from|by)\\s+([A-Za-z][A-Za-z .'/-]{1,39}?)(?=[.,!|/]|\\s(?:on|via|towards|ref|\\d)|$)"
        )

        // Kotak references look like "Ref no 123456789", "UPI/CR/51234...".
        private val REFERENCE_REGEX = Regex(
            "(?i)(?:ref(?:\\s*no)?\\.?\\s*|upi[/\\s-]*(?:cr|dr)?[/\\s-]*)([A-Za-z0-9]{6,40})"
        )

        private val NON_PAYMENT = listOf(
            "otp", "one time password", "login", "blocked", "limit",
            "kyc", "statement", "emi due", "minimum balance", "service request",
            "failed", "request", "reminder",
        )
    }
}

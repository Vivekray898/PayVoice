package com.vivekray898.payvoice.core.parser

import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.ParsedNotification
import com.vivekray898.payvoice.core.model.PaymentSource

/**
 * Parser for Google Pay (com.google.android.apps.nbu.paisa.user).
 *
 * Known notification shapes (refined on-device during Phase 1 testing):
 *  - "₹500 received from Rahul Sharma"          → credit, HIGH
 *  - "You received ₹1,200.50 from Amit"         → credit, HIGH
 *  - "₹500 paid to Shop" / "You paid ₹500 to X" → debit, never announced
 *  - "Payment of ₹500 to X failed"              → negative context, never announced
 *  - "Rahul sent you a request for ₹500"        → request, never announced
 */
class GooglePayParser : PaymentParser {

    override val source = PaymentSource.GOOGLE_PAY

    override fun parse(title: String?, text: String?): ParsedNotification? {
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val normalized = DirectionClassifier.normalize(combined)

        // Fast reject: obviously non-payment content (offers, invites, spam).
        if (NON_PAYMENT.any { normalized.contains(it) }) return null

        val (direction, confidence) = DirectionClassifier.classify(combined)
        val amount = AmountExtractor.extract(combined, preferLargest = true)
        val amountMinor = amount?.minorUnits
            ?: return if (direction == Direction.UNKNOWN) null
            // A directional notification without an amount is not announceable,
            // but still surfaced for diagnostics.
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
     * Sender extraction for phrases like "received from Rahul Sharma." and
     * GPay's real format "BINAY PAL paid you ₹1.00" — captures up to the next
     * sentence delimiter, bullet, digit or keyword.
     */
    private fun extractSender(text: String): String? {
        val m = PAID_YOU_REGEX.find(text)?.let { it.groupValues[1] }
            ?: SENDER_REGEX.find(text)?.groupValues?.get(1)
            ?: return null
        return m
            .trim()
            .trimEnd('.', ',', '-', '–', '!', '|', '•')
            .takeIf { it.length in 2..40 }
    }

    /** UPI reference/txn ids like "UPI: 512345678901" or "Txn ID: ABC12345". */
    private fun extractReference(text: String): String? =
        REFERENCE_REGEX.find(text)?.groupValues?.get(1)?.trim()?.take(40)

    companion object {
        private const val RAW_SNIPPET_MAX = 160

        private val SENDER_REGEX = Regex(
            "(?i)(?:from|by)\\s+([A-Za-z][A-Za-z .'-]{1,39}?)(?=[.,!|•]|\\s(?:on|via|for|using|at|\\d)|$)"
        )

        // Real GPay credit format: "BINAY PAL paid you ₹1.00".
        private val PAID_YOU_REGEX = Regex(
            "(?i)^\\s*([A-Za-z][A-Za-z .'-]{1,39}?)\\s+(?:paid|sent)\\s+you\\b"
        )

        private val REFERENCE_REGEX = Regex(
            "(?i)(?:txn(?:\\s*id)?|upi(?:\\s*ref(?:erence)?)?|ref(?:\\s*no)?\\.?)\\s*[:#-]?\\s*([A-Za-z0-9]{6,40})"
        )

        private val NON_PAYMENT = listOf(
            "cashback offer", "invite", "reward", "deal", "offer ends",
            "add money offer", "scratch card", "rate us", "verify your number",
            "failed", "request", "reminder",
        )
    }
}

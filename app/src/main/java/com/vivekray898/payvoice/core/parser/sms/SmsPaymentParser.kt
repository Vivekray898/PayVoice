package com.vivekray898.payvoice.core.parser.sms

import com.vivekray898.payvoice.core.model.CaptureSource
import com.vivekray898.payvoice.core.model.ParsedNotification
import com.vivekray898.payvoice.core.model.PaymentSource

/**
 * SMS parser architecture (Phase 4): one parser per bank family, all driven by
 * the shared deterministic classifier — no giant regex file. Each parser maps
 * a classified SMS into the same [ParsedNotification] the notification parsers
 * produce, so both channels feed one pipeline (Phase 11).
 */
interface SmsPaymentParser {
    /** Which capture channel this parser's results are tagged with. */
    val captureSource: CaptureSource

    /** Notification-equivalent source used for announcements/history. */
    val paymentSource: PaymentSource

    /** Order of preference when several parsers match the same sender. */
    val priority: Int get() = 0

    /**
     * Parses an SMS into a [ParsedNotification], or null when this SMS is not
     * a payment for this parser (wrong bank, non-payment, unclassifiable).
     */
    fun parse(sender: String, body: String, receivedAtMs: Long): ParsedNotification?
}

/** Shared implementation: classify → map. Bank-specific classes only configure. */
abstract class BaseSmsParser : SmsPaymentParser {

    protected abstract fun matches(sender: String, body: String, bank: String?): Boolean

    protected open fun displayLabel(bank: String?): String? = null

    final override fun parse(sender: String, body: String, receivedAtMs: Long): ParsedNotification? {
        if (!SmsSenderHints.looksLikeTransactionSender(sender)) return null
        val bank = SmsSenderHints.resolveBank(sender, body)
        if (!matches(sender, body, bank)) return null

        val result = SmsTransactionClassifier.classify(sender, body, bank)
        if (!result.isPayment) return null

        return ParsedNotification(
            source = paymentSource,
            sourceLabel = displayLabel(bank),
            direction = result.direction,
            confidence = when (result.confidence) {
                SmsTransactionClassifier.Confidence.HIGH ->
                    com.vivekray898.payvoice.core.model.Confidence.HIGH
                else -> com.vivekray898.payvoice.core.model.Confidence.MEDIUM
            },
            amountMinor = result.amountMinor,
            currency = "INR",
            // Presentation-only normalization at parse time (spec §12): the
            // parsed event carries "Ms USHA DAS" as "Ms Usha Das" everywhere
            // (announcement, history, diagnostics). Identity is never inferred.
            senderName = SmsNameNormalizer.normalize(result.payer),
            referenceId = result.reference,
            rawTitle = "SMS:$sender",
            rawText = body.take(160),
        )
    }
}

/** Kotak bank SMS (KKBK…, KOTAK…, 811 wording). */
class KotakSmsParser : BaseSmsParser() {
    override val captureSource = CaptureSource.SMS_KOTAK

    // SMS channel has no app-notification source; GOOGLE_PAY is the
    // announcement source family and the bank stays visible via sourceLabel.
    override val paymentSource = PaymentSource.GOOGLE_PAY
    override val priority = 30

    override fun matches(sender: String, body: String, bank: String?): Boolean =
        bank == "Kotak"

    // History/diagnostics show the actual bank, not the generic source family.
    override fun displayLabel(bank: String?): String = bank ?: "Kotak"
}

/** Google Pay / UPI-flow SMS forwarded by PSP/bank senders. */
class GPaySmsParser : BaseSmsParser() {
    override val captureSource = CaptureSource.SMS_BANK
    override val paymentSource = PaymentSource.GOOGLE_PAY
    override val priority = 20

    override fun matches(sender: String, body: String, bank: String?): Boolean =
        bank == "Google Pay" ||
            // Generic UPI credit mentioning GPay-style flow markers.
            (body.contains("upi", ignoreCase = true) &&
                body.contains("googlepay", ignoreCase = true) ||
                body.contains("gpay", ignoreCase = true))
}

/** Any other transactional bank SMS — parsed, tagged generically. */
class GenericBankSmsParser : BaseSmsParser() {
    override val captureSource = CaptureSource.SMS_BANK
    override val paymentSource = PaymentSource.GOOGLE_PAY // announcement source family
    override val priority = 0

    override fun matches(sender: String, body: String, bank: String?): Boolean = true

    override fun displayLabel(bank: String?): String = bank ?: "Bank SMS"
}

/** Picks the highest-priority parser that accepts the SMS. */
object SmsPaymentParserRegistry {
    private val parsers: List<SmsPaymentParser> = listOf(
        KotakSmsParser(),
        GPaySmsParser(),
        GenericBankSmsParser(),
    )

    fun parse(sender: String, body: String, receivedAtMs: Long): ParsedNotification? =
        parsers.sortedByDescending { it.priority }
            .firstNotNullOfOrNull { it.parse(sender, body, receivedAtMs) }
}

package com.vivekray898.payvoice.core.parser.sms

import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.parser.AmountExtractor
import com.vivekray898.payvoice.core.parser.DirectionClassifier

/**
 * Deterministic, debuggable SMS transaction classifier (Phases 5, 6, 10).
 * Weighted signals — never machine learning. A message needs BOTH positive
 * payment evidence AND no strong negative context to be classified.
 */
object SmsTransactionClassifier {

    data class Result(
        val direction: Direction,
        val confidence: Confidence,
        val score: Int,
        val amountMinor: Long?,
        val reference: String?,
        val payer: String?,
        /** True when this is a real incoming/outgoing payment worth parsing. */
        val isPayment: Boolean,
        /** Human-readable hit reasons, shown in Diagnostics only. */
        val reasons: List<String>,
    )

    enum class Confidence { HIGH, MEDIUM, LOW, NOT_PAYMENT }

    // Weighted signal tables (Phase 10). Order matters only for readability.
    private val RECEIVED_SIGNALS = listOf(
        "credited", "received", "money received", "payment received",
        "credit", "upi received", "credited with", "a/c credited",
        "account credited", "amount credited", "deposited",
    )
    private val SENT_SIGNALS = listOf(
        "debited", "sent", "paid", "payment made", "transferred", "withdrawn",
        "debit card used", "purchased", "payment",
    )
    private val UPI_SIGNALS = listOf("upi", "utr", "rrn", "txn", "transaction", "reference", "ref no", "refno")
    private val NEGATIVE_STRONG = listOf(
        "otp", "one time password", "password", "pin ",
        "offer", "cashback offer", "discount", "win ", "lottery", "click here",
        "subscribe", "promo", "kyc", "verify your",
    )
    private val NEGATIVE_MODERATE = listOf(
        "bill", "due", "reminder", "emi", "minimum balance", "limit",
        "failed", "declined", "returned", "request", "awaited", "pending",
    )

    private const val W_RECEIVED = 50
    private const val W_SENT = -60
    private const val W_UPI = 25
    private const val W_AMOUNT = 20
    private const val W_BANK = 20
    private const val W_REFERENCE = 15
    private const val W_NEGATIVE_STRONG = -80
    private const val W_NEGATIVE_MODERATE = -50

    private const val THRESHOLD_HIGH = 85
    private const val THRESHOLD_MEDIUM = 55

    fun classify(sender: String, body: String, bank: String?): Result {
        val normalized = DirectionClassifier.normalize(body)
        val reasons = mutableListOf<String>()
        var score = 0

        val hitReceived = RECEIVED_SIGNALS.filter { normalized.contains(it) }
        val hitSent = SENT_SIGNALS.filter { normalized.contains(it) }
        val negativeStrong = NEGATIVE_STRONG.firstOrNull { normalized.contains(it) }
        val negativeModerate = NEGATIVE_MODERATE.firstOrNull { normalized.contains(it) }

        // OTP/promo/bill messages are never payments — decide fast.
        if (negativeStrong != null) {
            return Result(
                Direction.UNKNOWN, Confidence.NOT_PAYMENT, W_NEGATIVE_STRONG,
                null, null, null, false, listOf("negative: $negativeStrong"),
            )
        }

        val amount = AmountExtractor.extract(body, preferLargest = false)
        val reference = SmsReferenceExtractor.extract(body)
        val payer = extractPayer(body)

        // Two ledgers: `score` grades RECEIVED-announcement confidence (sent
        // evidence penalizes); `txnScore` grades is-this-a-transaction-at-all
        // (sent evidence counts POSITIVELY so SENT direction is classifiable
        // and the pipeline can suppress it explicitly rather than drop it).
        var txnScore = 0

        if (hitReceived.isNotEmpty()) {
            score += W_RECEIVED
            txnScore += W_RECEIVED
            reasons += "received: ${hitReceived.first()}"
        }
        if (hitSent.isNotEmpty()) {
            score += W_SENT
            txnScore += -W_SENT // 60: sent evidence IS transaction evidence
            reasons += "sent: ${hitSent.first()}"
        }
        val hitUpi = UPI_SIGNALS.firstOrNull { normalized.contains(it) }
        if (hitUpi != null) {
            score += W_UPI
            txnScore += W_UPI
            reasons += "upi-indicator: $hitUpi"
        }
        if (amount != null) {
            score += W_AMOUNT
            txnScore += W_AMOUNT
            reasons += "amount: ${amount.minorUnits}"
        }
        if (bank != null) {
            score += W_BANK
            txnScore += W_BANK
            reasons += "bank: $bank"
        }
        if (reference != null) {
            score += W_REFERENCE
            txnScore += W_REFERENCE
            reasons += "reference: ${reference.take(6)}…"
        }
        if (negativeModerate != null) {
            score += W_NEGATIVE_MODERATE
            txnScore += W_NEGATIVE_MODERATE
            reasons += "negative: $negativeModerate"
        }

        val direction = when {
            hitSent.isNotEmpty() && hitReceived.isEmpty() -> Direction.SENT
            hitReceived.isNotEmpty() && hitSent.isEmpty() -> Direction.RECEIVED
            hitReceived.isNotEmpty() && hitSent.isNotEmpty() ->
                // Both families present: let the direction classifier arbitrate.
                DirectionClassifier.classify(body).first
            else -> Direction.UNKNOWN
        }

        val confidence = when {
            score >= THRESHOLD_HIGH -> Confidence.HIGH
            score >= THRESHOLD_MEDIUM -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        // A transaction requires: amount + direction + no strong negative +
        // at least MEDIUM transaction evidence. SENT direction is a valid
        // classification (pipeline suppresses it from announcements).
        val isPayment = amount != null && direction != Direction.UNKNOWN &&
            txnScore >= THRESHOLD_MEDIUM

        val effectiveConfidence = when {
            !isPayment -> Confidence.LOW
            direction == Direction.SENT ->
                if (txnScore >= THRESHOLD_HIGH) Confidence.HIGH else Confidence.MEDIUM
            else -> confidence
        }

        return Result(
            direction = direction,
            confidence = effectiveConfidence,
            score = score,
            amountMinor = amount?.minorUnits,
            reference = reference,
            payer = payer,
            isPayment = isPayment,
            reasons = reasons,
        )
    }

    /** Payer extraction: "from Rahul", "from rahul@ybl", "by +91-…" (optional). */
    private fun extractPayer(body: String): String? {
        // Stop-words include "in"/"to": Kotak credit SMS read
        // "from Ms USHA DAS in your Kotak811 a/c XX8521" — without them the
        // payer swallows the account clause and the announcement is wrong.
        val m = Regex(
            "(?i)(?:from|by)\\s+([A-Za-z0-9][A-Za-z0-9.@ _'-]{1,39}?)(?=[.,!|/]|\\s(?:on|in|to|via|towards|ref|dated|\\d)|$)"
        ).find(body) ?: return null
        return m.groupValues[1].trim()
            .trimEnd('.', ',', '-', '/', '|')
            .takeIf { it.length in 2..40 }
    }
}

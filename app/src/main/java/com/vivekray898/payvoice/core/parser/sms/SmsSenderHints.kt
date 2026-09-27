package com.vivekray898.payvoice.core.parser.sms

/**
 * Configurable bank/sender hints (Phase 9). Indian banks use varying
 * alphanumeric sender IDs (KKBK, KOTAKBK, HDFCBK…), so nothing here is
 * hardcoded as exhaustive — hints are combined with body text, transaction
 * language and UPI identifiers to classify. Extend this table as real
 * sender IDs are observed in diagnostics.
 */
object SmsSenderHints {

    /** Known sender-ID fragments → bank name. Matched case-insensitively. */
    private val SENDER_HINTS: Map<String, String> = mapOf(
        "KKBK" to "Kotak",
        "KOTAK" to "Kotak",
        "KOTAK811" to "Kotak",
        "KTK" to "Kotak",
        "HDFC" to "HDFC",
        "ICICI" to "ICICI",
        "SBI" to "SBI",
        "SBIN" to "SBI",
        "AXIS" to "Axis",
        "PAYTM" to "Paytm",
        "AMZNP" to "Amazon Pay",
        "GPAY" to "Google Pay",
        "GOOGLE" to "Google Pay",
    )

    /** Body keywords → bank name (used when sender ID is generic/numeric). */
    private val BODY_HINTS: Map<String, String> = mapOf(
        "kotak" to "Kotak",
        "kotak mahindra" to "Kotak",
        "811" to "Kotak",
        "hdfc" to "HDFC",
        "icici" to "ICICI",
        "state bank" to "SBI",
        "axis bank" to "Axis",
    )

    /** Resolves the bank from the SMS sender address (e.g. "KKBK6789"). */
    fun bankFromSender(sender: String): String? {
        val s = sender.uppercase()
        return SENDER_HINTS.entries
            .filter { s.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Resolves the bank from body text (fallback when sender is generic). */
    fun bankFromBody(body: String): String? {
        val b = body.lowercase()
        return BODY_HINTS.entries
            .filter { b.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Combined resolution: sender wins, body breaks ties. */
    fun resolveBank(sender: String, body: String): String? =
        bankFromSender(sender) ?: bankFromBody(body)

    /**
     * Transactional-SMS gate: Indian transactional SMS originate from
     * short alphanumeric sender IDs (e.g. KKBK6789) or clearly identified
     * banks. Personal-number SMS (+91…) are ignored for privacy.
     */
    fun looksLikeTransactionSender(sender: String): Boolean {
        val compact = sender.filter { !it.isWhitespace() }
        if (compact.none { it.isLetterOrDigit() }) return false
        val alphaCount = compact.count { it.isLetter() }
        val digitOnly = compact.all { it.isDigit() || it == '+' }
        // Pure numbers = personal/business phone number → not a bank gateway.
        if (digitOnly && compact.length >= 8) return false
        // At least some letters (bank sender IDs) or a known hint.
        return alphaCount >= 3 || bankFromSender(sender) != null
    }
}

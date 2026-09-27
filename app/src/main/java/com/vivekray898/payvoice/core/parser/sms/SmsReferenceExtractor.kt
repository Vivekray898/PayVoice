package com.vivekray898.payvoice.core.parser.sms

/**
 * Extracts UPI/transaction references from SMS bodies (Phase 5/12). A shared
 * reference is the strongest dedup key: the same UTR arriving via both a bank
 * notification and a bank SMS must collapse to ONE payment.
 */
object SmsReferenceExtractor {

    // UPI/123456789012, UPI Ref no 123456789012, UTR-123..., RRN 123...,
    // Ref no 880123456, TxnRef: ABC12345
    private val PATTERNS = listOf(
        Regex("(?i)\\bupi[/\\s-]*(?:ref(?:\\s*(?:no|number))?\\.?\\s*[:#-]?\\s*)?([0-9]{9,18})"),
        Regex("(?i)\\butr[:#-]?\\s*([A-Za-z0-9]{8,22})"),
        Regex("(?i)\\brrn[:#-]?\\s*([0-9]{9,18})"),
        Regex("(?i)\\b(?:ref|reference)(?:\\s*(?:no|number))?\\.?\\s*[:#-]\\s*([A-Za-z0-9]{6,22})"),
        Regex("(?i)\\btxn(?:\\s*id)?\\.?\\s*[:#-]\\s*([A-Za-z0-9]{6,22})"),
    )

    /** First matching reference, normalized (uppercase, trimmed). */
    fun extract(body: String): String? {
        for (pattern in PATTERNS) {
            val m = pattern.find(body) ?: continue
            val value = m.groupValues[1].trim().uppercase()
            if (value.length in 6..22) return value
        }
        return null
    }
}

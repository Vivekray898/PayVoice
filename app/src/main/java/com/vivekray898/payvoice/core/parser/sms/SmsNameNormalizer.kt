package com.vivekray898.payvoice.core.parser.sms

/**
 * Presentation-only normalization of payer names from bank SMS
 * ("Ms USHA DAS" → "Ms Usha Das"). Rules (spec §12):
 *  - never infers identity, invents names, or merges across sources;
 *  - honorifics keep their standard short form;
 *  - single letters are initials and stay capitalized ("S K Sharma");
 *  - dotted initials keep the dot ("A. K. Gupta");
 *  - UPI handles (contain '@') are lowercased as-is.
 */
object SmsNameNormalizer {

    private val HONORIFICS = setOf("mr", "mrs", "ms", "dr", "prof", "shri", "smt", "m/s")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length !in 2..40) return null
        if (cleaned.contains('@')) {
            return cleaned.lowercase().takeIf { it.length in 3..40 }
        }

        val out = cleaned.split(' ').joinToString(" ") { token ->
            if (token.isEmpty()) return@joinToString token
            val hasDot = token.endsWith(".")
            val core = token.trim('.').lowercase()
            when {
                core.isEmpty() -> token
                core.length == 1 -> core.uppercase() + if (hasDot) "." else ""
                core in HONORIFICS -> core.replaceFirstChar { it.uppercase() }
                else -> core.replaceFirstChar { it.uppercase() }
            }
        }.trim()
        return out.ifBlank { null }
    }
}

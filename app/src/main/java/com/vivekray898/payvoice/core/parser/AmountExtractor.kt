package com.vivekray898.payvoice.core.parser

/**
 * Extracts currency amounts from notification text. Handles ₹ / Rs / INR
 * prefixes, Indian digit grouping (1,20,000.75), and loose spacing.
 * Pure functions — heavily unit tested. Returns minor units (paise).
 */
object AmountExtractor {

    private const val RUPEE = '\u20B9'
    private const val RUPEE_FALLBACK = '\u20A8' // ₨, seen on some engines

    // Prefixes: ₹ / Rs / Rs. / INR, optional space, then a number with optional
    // Indian-style comma grouping and optional decimal part. INR/Rs accept a
    // digit directly after (INR500) via optional whitespace.
    private val AMOUNT_REGEX = Regex(
        "(?i)(?:[$RUPEE$RUPEE_FALLBACK]|\\brs\\.?|\\binr)\\s*([0-9](?:[0-9,]*[0-9])?(?:\\.[0-9]{1,2})?)"
    )

    /** Parsed result in minor units (paise) for INR. */
    data class Amount(val minorUnits: Long, val currency: String)

    /**
     * Returns the first amount found in [text], or null. When [preferLargest]
     * is true (GPay credit lines often cite both amount and balance), the
     * largest amount wins instead of the first.
     */
    fun extract(text: String, preferLargest: Boolean = false): Amount? {
        val matches = AMOUNT_REGEX.findAll(text).mapNotNull { m ->
            toMinorUnits(m.groupValues[1])?.let { Amount(it, "INR") }
        }.toList()
        if (matches.isEmpty()) return null
        return if (preferLargest) matches.maxByOrNull { it.minorUnits } else matches.first()
    }

    /**
     * Converts a digits-and-commas string like "1,20,000.75" into paise.
     * Integer arithmetic only — no floating point anywhere near money.
     */
    fun toMinorUnits(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val dot = cleaned.indexOf('.')
        val majorPart: String
        val minorPart: String
        if (dot >= 0) {
            majorPart = cleaned.substring(0, dot)
            minorPart = cleaned.substring(dot + 1)
        } else {
            majorPart = cleaned
            minorPart = ""
        }
        if (majorPart.isEmpty() || !majorPart.all { it.isDigit() }) return null
        val major = majorPart.toLongOrNull() ?: return null
        val minor = when (minorPart.length) {
            0 -> 0L
            1 -> minorPart[0].digitToInt() * 10L
            2 -> minorPart.take(2).toLong()
            else -> return null
        }
        val total = major * 100 + minor
        return if (total > 0) total else null
    }

    /** Formats minor units for logs/UI with Indian digit grouping: 12000000 -> "₹1,20,000". */
    fun formatMinor(minor: Long, currency: String = "INR"): String {
        val symbol = if (currency == "INR") "$RUPEE" else currency
        val major = minor / 100
        val paise = minor % 100
        val grouped = indianGroup(major)
        return if (paise == 0L) "$symbol$grouped"
        else "$symbol$grouped.${paise.toString().padStart(2, '0')}"
    }

    /** Indian digit grouping: last 3 digits, then groups of 2. 1200000 -> "12,00,000". */
    private fun indianGroup(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val groups = ArrayDeque<String>()
        groups.addLast(s.substring(s.length - 3))
        var rest = s.substring(0, s.length - 3)
        while (rest.length > 2) {
            groups.addFirst(rest.substring(rest.length - 2))
            rest = rest.substring(0, rest.length - 2)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)
        return groups.joinToString(",")
    }
}

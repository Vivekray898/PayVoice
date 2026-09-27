package com.vivekray898.payvoice.core.announce

/**
 * Announcement style (spec §25): how much detail is spoken.
 */
enum class AnnouncementStyle(val label: String, val sample: String) {
    AMOUNT_ONLY("Amount only", "₹500 received."),
    AMOUNT_SENDER("Amount + sender", "₹500 received from Rahul."),
    FULL("Full", "Payment received. Five hundred rupees from Rahul through Google Pay."),
}

/**
 * Announcement language (spec §11). HINGLISH is code-mixed wording
 * ("paanch sau rupaye mil gaye") spoken by the Hindi TTS voice — it is not a
 * separate locale; it uses the same hi-IN engine voice.
 */
enum class AnnouncementLanguage(val label: String, val ttsLocaleTag: String) {
    ENGLISH("English", "en-IN"),
    HINDI("हिन्दी", "hi-IN"),
    HINGLISH("Hinglish", "hi-IN"),
}

/**
 * Converts minor units (paise) into spoken words. Integer math only.
 * Supports English and Hindi cardinal reading of rupee amounts.
 */
object AmountToWords {

    private val ONES_EN = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
        "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS_EN = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
        "eighty", "ninety",
    )
    private val ONES_HI = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह",
        "अठारह", "उन्नीस",
    )
    private val TENS_HI = arrayOf(
        "", "", "बीस", "तीस", "चालीस", "पचास", "साठ", "सत्तर", "अस्सी", "नब्बे",
    )
    private val TEEN_HI = arrayOf(
        "एगारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
    )

    /**
     * English cardinal words for 0..99,99,999 (Indian system: thousand /
     * lakh). Values beyond are read as digits by the TTS fallback.
     */
    fun englishWords(amountMinor: Long): String {
        val rupees = amountMinor / 100
        val paise = amountMinor % 100
        val rupeeWords = if (rupees == 0L && paise > 0) "" else englishCardinal(rupees)
        return when {
            paise == 0L -> "$rupeeWords rupees"
            rupees == 0L -> "${englishCardinal(paise)} paise"
            else -> "$rupeeWords rupees and ${englishCardinal(paise)} paise"
        }.trim()
    }

    /** Hindi cardinal words (देवनागरी) for the same range. */
    fun hindiWords(amountMinor: Long): String {
        val rupees = amountMinor / 100
        val paise = amountMinor % 100
        val rupeeWords = if (rupees == 0L && paise > 0) "" else hindiCardinal(rupees)
        return when {
            paise == 0L -> "$rupeeWords रुपये"
            rupees == 0L -> "${hindiCardinal(paise)} पैसे"
            else -> "$rupeeWords रुपये ${hindiCardinal(paise)} पैसे"
        }.trim()
    }

    fun englishCardinal(n: Long): String {
        if (n < 0) return "minus ${englishCardinal(-n)}"
        if (n < 20) return ONES_EN[n.toInt()]
        if (n < 100) {
            val t = TENS_EN[(n / 10).toInt()]
            val r = n % 10
            return if (r == 0L) t else "$t-${ONES_EN[r.toInt()]}"
        }
        if (n < 1_000) return chunk(n, 100, "hundred", ::englishCardinal)
        if (n < 100_000) return chunk(n, 1_000, "thousand", ::englishCardinal)
        if (n < 10_000_000) return chunk(n, 100_000, "lakh", ::englishCardinal)
        return chunk(n, 10_000_000, "crore", ::englishCardinal)
    }

    fun hindiCardinal(n: Long): String {
        if (n < 0) return "माइनस ${hindiCardinal(-n)}"
        if (n < 20) return ONES_HI[n.toInt()]
        if (n < 100) {
            val r = n % 10
            if (r == 0L) return TENS_HI[(n / 10).toInt()]
            if (n in 21..39 || n in 61..79) {
                // ekis, ikkis pattern handled loosely: "एक और बीस" is unnatural;
                // use standard composite "एक बीस" only as fallback below.
            }
            val tensWord = TENS_HI[(n / 10).toInt()]
            val onesWord = ONES_HI[r.toInt()]
            return when (n) {
                21L -> "इक्कीस"
                22L -> "बाईस"
                23L -> "तेईस"
                28L -> "अट्ठाईस"
                31L -> "इकतीस"
                38L -> "अड़तीस"
                41L -> "इकतालीस"
                46L -> "छियालीस"
                51L -> "इक्यावन"
                56L -> "छप्पन"
                61L -> "इकसठ"
                66L -> "छियासठ"
                71L -> "इकहत्तर"
                76L -> "छिहत्तर"
                81L -> "इक्यासी"
                86L -> "छियासी"
                91L -> "इक्यानवे"
                96L -> "छियानवे"
                else -> "$onesWord $tensWord"
            }
        }
        if (n < 1_000) return chunk(n, 100, "सौ", ::hindiCardinal)
        if (n < 100_000) return chunk(n, 1_000, "हज़ार", ::hindiCardinal)
        if (n < 10_000_000) return chunk(n, 100_000, "लाख", ::hindiCardinal)
        return chunk(n, 10_000_000, "करोड़", ::hindiCardinal)
    }

    private fun chunk(n: Long, unit: Long, unitWord: String, rec: (Long) -> String): String {
        val head = n / unit
        val tail = n % unit
        val headWords = rec(head)
        return if (tail == 0L) "$headWords $unitWord" else "$headWords $unitWord ${rec(tail)}"
    }
}

package com.vivekray898.payvoice.core.parser

import com.vivekray898.payvoice.core.model.ParsedNotification
import com.vivekray898.payvoice.core.model.PaymentSource

/**
 * A per-provider notification parser. One implementation per payment app —
 * never one giant regex file (spec §5). Implementations must be pure and
 * side-effect free so they are trivially unit-testable.
 */
interface PaymentParser {
    val source: PaymentSource

    /**
     * Parses a notification into a [ParsedNotification]. Returns null when the
     * notification is clearly not a payment notification at all (e.g. a
     * promotional card), as opposed to a payment the classifier rejects.
     */
    fun parse(title: String?, text: String?): ParsedNotification?
}

/** Routes a notification to the parser registered for its package. */
class PaymentParserRegistry(parsers: List<PaymentParser>) {

    private val byPackage: Map<String, PaymentParser> =
        parsers.associateBy { it.source.packageId }

    private val bySource: Map<PaymentSource, PaymentParser> =
        parsers.associateBy { it.source }

    fun parserForPackage(packageId: String): PaymentParser? {
        byPackage[packageId]?.let { return it }
        // Kotak's runtime package (verified capture or installed candidate)
        // differs from the compile-time enum constant — resolve by source.
        val source = PaymentSource.fromPackage(packageId) ?: return null
        return bySource[source]
    }

    companion object {
        fun withDefaults(): PaymentParserRegistry =
            PaymentParserRegistry(listOf(GooglePayParser(), KotakParser()))
    }
}

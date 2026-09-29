package com.vivekray898.payvoice.core.parser

/**
 * Reliability fix: GPay sometimes places the payment line in
 * [android.app.Notification.EXTRA_BIG_TEXT] or EXTRA_SUB_TEXT instead of
 * EXTRA_TEXT (varies by GPay version and notification style). A parser that
 * reads only EXTRA_TEXT silently drops those payments.
 *
 * This resolver produces the candidate (title, body) pairs to try, best
 * first. bigText wins over text because on notifications that carry both,
 * bigText is the expanded form that contains the full payment line, while
 * text is often a truncated/collapsed rendering of the SAME content — trying
 * text first can mis-parse (e.g. amount cut off). subText carries auxiliary
 * lines and is tried last. Blanks are skipped. Pure and unit-testable.
 */
object NotificationTextResolver {

    /** Candidate (title, body) parse inputs, best first; never empty-blank bodies. */
    fun candidates(
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
    ): List<Pair<String?, String>> = buildList {
        for (body in listOf(bigText, text, subText)) {
            if (!body.isNullOrBlank()) add(title to body)
        }
    }
}

package com.vivekray898.payvoice.core.parser

import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction

/**
 * Classifies a notification as money-in, money-out, or undecidable using
 * weighted keyword families. Weighted (not boolean) so mixed sentences resolve
 * sensibly — e.g. "failed transaction" must never be classified as RECEIVED.
 */
object DirectionClassifier {

    private class W(val phrase: String, val weight: Int)

    // Strong, unambiguous credit family.
    private val RECEIVED_STRONG = listOf(
        W("received", 3),
        W("credited", 3),
        W("credit", 2),
        W("money received", 4),
        W("payment received", 4),
        W("amount credited", 4),
        W("credited to your account", 4),
        W("received from", 4),
        W("credited by", 4),
    )

    // Strong debit family.
    private val SENT_STRONG = listOf(
        W("sent", 3),
        W("debited", 3),
        W("debit", 2),
        W("paid", 2),
        W("paid to", 4),
        W("payment made", 4),
        W("sent to", 4),
        W("debited from your account", 4),
    )

    // Negative signals: failure/refund-context words that suppress announceable
    // classification entirely.
    private val NEGATIVE = listOf(
        W("failed", -5),
        W("failure", -5),
        W("declined", -5),
        W("rejected", -5),
        W("pending", -3),
        W("refund", -3),
        W("cancelled", -5),
        W("canceled", -5),
        W("request", -5),
        W("reminder", -3),
        W("verified", -1),
        W("reward", -3),
        W("cashback offer", -3),
        W("bill", -1),
    )

    /**
     * Classifies normalized [text] (title + content). Rules:
     *  - any strong negative context wins outright (never announce failures)
     *  - otherwise the stronger of received/sent families decides direction
     *  - confidence scales with absolute score and absence of competing family
     */
    fun classify(text: String): Pair<Direction, Confidence> {
        val t = normalize(text)
        if (t.isBlank()) return Direction.UNKNOWN to Confidence.LOW

        val negScore = NEGATIVE.sumOf { if (t.contains(it.phrase)) it.weight else 0 }
        val inScore = RECEIVED_STRONG.sumOf { if (t.contains(it.phrase)) it.weight else 0 }
        val outScore = SENT_STRONG.sumOf { if (t.contains(it.phrase)) it.weight else 0 }

        if (negScore <= -5) return Direction.UNKNOWN to Confidence.LOW

        val margin = inScore - outScore
        val direction = when {
            margin > 0 -> Direction.RECEIVED
            margin < 0 -> Direction.SENT
            else -> Direction.UNKNOWN
        }

        val confidence = when (direction) {
            Direction.UNKNOWN -> Confidence.LOW
            Direction.RECEIVED -> confidenceFor(inScore, outScore)
            Direction.SENT -> confidenceFor(outScore, inScore)
        }
        return direction to confidence
    }

    private fun confidenceFor(winner: Int, loser: Int): Confidence = when {
        winner >= 3 && loser == 0 -> Confidence.HIGH
        winner >= 2 && loser <= 1 -> Confidence.HIGH
        winner > loser -> Confidence.MEDIUM
        else -> Confidence.LOW
    }

    fun normalize(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ").trim()
}

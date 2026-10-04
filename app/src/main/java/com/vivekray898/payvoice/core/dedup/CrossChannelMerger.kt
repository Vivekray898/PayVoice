package com.vivekray898.payvoice.core.dedup

/**
 * Reconciles ONE payment that arrives on two different channels — a Google Pay
 * notification and a bank SMS, say. Neither carries a UPI/Txn reference, so
 * [com.vivekray898.payvoice.core.parser.Fingerprinter] keys them differently
 * and the reference-less dedupe cannot collapse them. Without this step the
 * customer hears the same payment twice; with the old amount+5-minute bucket
 * key, the price of avoiding that double was *losing real payments*. These
 * rules buy the double back without reintroducing the loss.
 *
 * The rules are deliberately conservative. When a rule cannot be evaluated
 * confidently the decision is [Decision.ANNOUNCE]: a duplicate announcement is
 * a nuisance, a missed payment is the failure this whole change exists to fix.
 */
object CrossChannelMerger {

    /**
     * How far apart two cross-channel captures of one payment may be. Wide
     * enough for bank SMS delivery lag, far narrower than the 5-minute bucket
     * it replaces — and narrower than any realistic gap between two *distinct*
     * payments from the same person for the same amount.
     */
    const val CROSS_CHANNEL_MERGE_WINDOW_MS = 90_000L

    /** The inputs a merge decision needs. Nothing here is payment content. */
    data class Incoming(
        val amountMinor: Long,
        val postedAtMs: Long,
        val captureSource: String,
        val hasReference: Boolean,
    )

    /** The already-announced event being considered as the earlier half. */
    data class Earlier(
        val eventId: String,
        val amountMinor: Long,
        val postedAtMs: Long,
        val captureSource: String,
        val hasReference: Boolean,
    )

    sealed interface Decision {
        /** Merge: the same payment, already spoken. Do NOT announce again. */
        data class Merge(val earlier: Earlier) : Decision

        /** Announce: distinct payment, or not confident enough to merge. */
        data class Announce(val reason: Reason) : Decision
    }

    /**
     * Why we did not merge. Countable tokens, so the debug trace can answer
     * "how often was each rule the thing that saved a duplicate announcement".
     */
    enum class Reason {
        /** Merged. */
        MERGED,

        /** Rule 1: different amounts are never the same payment. */
        DIFFERENT_AMOUNT,

        /** Rule 2: same channel — never merge without a shared reference. */
        SAME_CHANNEL,

        /** Rule 3: outside [CROSS_CHANNEL_MERGE_WINDOW_MS]. */
        OUTSIDE_WINDOW,

        /** Rule 4: a reference exists, so the reference decides, not the window. */
        REFERENCE_PRESENT,

        /** Nothing in the store to merge with. */
        NO_CANDIDATE,
    }

    /**
     * Applies the four rules, in order. ALL must hold to merge:
     *  1. same amount;
     *  2. different channels;
     *  3. within [CROSS_CHANNEL_MERGE_WINDOW_MS];
     *  4. neither side carries a UPI/Txn reference — if either does, the
     *     reference is authoritative and the reference-keyed dedupe already
     *     had its chance.
     *
     * [earlier] is the most recent candidate the dedupe store offered; it is
     * already the newest event for this amount, so the first failing rule is
     * the one reported.
     */
    fun decide(incoming: Incoming, earlier: Earlier?): Decision {
        if (earlier == null) return Decision.Announce(Reason.NO_CANDIDATE)
        if (incoming.amountMinor != earlier.amountMinor) {
            return Decision.Announce(Reason.DIFFERENT_AMOUNT)
        }
        if (incoming.captureSource == earlier.captureSource) {
            return Decision.Announce(Reason.SAME_CHANNEL)
        }
        val gapMs = kotlin.math.abs(incoming.postedAtMs - earlier.postedAtMs)
        if (gapMs > CROSS_CHANNEL_MERGE_WINDOW_MS) {
            return Decision.Announce(Reason.OUTSIDE_WINDOW)
        }
        if (incoming.hasReference || earlier.hasReference) {
            return Decision.Announce(Reason.REFERENCE_PRESENT)
        }
        return Decision.Merge(earlier)
    }

    /** Trace token for a decision — stable, countable, never prose. */
    fun reasonToken(decision: Decision): String = when (decision) {
        is Decision.Merge -> "merge-cross-channel"
        is Decision.Announce -> "merge-keep-${decision.reason.name.lowercase().replace('_', '-')}"
    }
}

package com.vivekray898.payvoice.core.trace

import java.util.UUID

/**
 * End-to-end pipeline tracing (Step 2 of the reliability work).
 *
 * Every payment gets a correlation id minted **at capture time**, before the
 * parser runs, and every hop it reaches appends one row. The point is to be
 * able to answer, for one real payment: *which hop did it stop at, and why?*
 *
 * Design constraints this obeys:
 *  - **Debug only.** [PaymentTrace.install] is a no-op unless the process is
 *    `FLAG_DEBUGGABLE`, so a release build writes no trace rows and allocates no
 *    ids. Every entry point here checks [enabled] first.
 *  - **No payment content, ever.** Amounts, sender names, phone numbers and
 *    notification text never reach this file. Raw capture text is reduced to a
 *    *shape* by [maskText] before it is recorded — see that function for the
 *    exact guarantee.
 *  - **Never on the critical path.** Hop writes go through a sink the caller
 *    launches on its own scope; a trace write can never delay or block an
 *    announcement. Writes are additionally best-effort (`runCatching` inside
 *    the sink).
 */
object PaymentTrace {

    /** The ten hop outcomes the reliability work asks for. */
    enum class Outcome {
        CAPTURED,
        PARSED,
        DEDUPED,
        /**
         * A cross-channel merge decision — recorded for BOTH outcomes, so the
         * trace can show which rule kept a second announcement (or let it
         * through). `MERGED` remains a terminal, non-announcing outcome.
         */
        MERGED,
        STORED,
        UPLOADED,
        FCM_SENT,
        FCM_RECEIVED,
        QUEUED,
        SPOKEN,
        DROPPED,
    }

    /**
     * One recorded hop.
     *
     * @param correlationId minted at capture; ties every hop of one payment.
     * @param outcome which hop this row is.
     * @param reason populated for `DEDUPED(reason)` and `DROPPED(reason)`; a
     *   stable short token (e.g. `gate-direction`, `upload-no-auth`) so the
     *   findings table can count causes rather than read prose.
     * @param detail extra non-identifying context (hop counts, flags,
     *   parser/parser-id names). Never payment content.
     */
    data class Hop(
        val correlationId: String,
        val outcome: Outcome,
        val atMs: Long,
        val reason: String? = null,
        val detail: String? = null,
    )

    /**
     * Raw capture metadata for the row written at `CAPTURED`.
     *
     * Everything here is structural. The notification text is never stored —
     * only [textShape], which [maskText] derives without echoing any of it.
     */
    data class CaptureMeta(
        val packageName: String,
        val notificationId: Int,
        val tag: String?,
        val category: String?,
        val flags: Int,
        val postedAtMs: Long,
        /** True when this post replaced an existing notification (same key). */
        val isUpdate: Boolean,
        val textShape: String,
    )

    /** Where hop rows go. Installed once, debug builds only. */
    interface Sink {
        fun write(hop: Hop)
        fun writeCapture(hop: Hop, meta: CaptureMeta)
    }

    /** The sink used when tracing is not installed — allocation-free no-op. */
    object NoopSink : Sink {
        override fun write(hop: Hop) = Unit
        override fun writeCapture(hop: Hop, meta: CaptureMeta) = Unit
    }

    @Volatile
    private var sink: Sink = NoopSink

    @Volatile
    private var enabledFlag: Boolean = false

    val enabled: Boolean get() = enabledFlag

    /**
     * Installs the real sink. Callers **must** only invoke this for a
     * debuggable process; [TraceStore.installIfDebuggable] is the intended
     * entry point and enforces that.
     */
    fun install(traceSink: Sink) {
        sink = traceSink
        enabledFlag = true
    }

    /** Test/teardown hook. */
    fun disable() {
        sink = NoopSink
        enabledFlag = false
    }

    /**
     * Mints the correlation id for one payment. Called at capture, before any
     * parsing, so a payment that dies in the parser still has an id to search
     * for. Only 8 hex chars — enough to group, short enough to read in logcat.
     */
    fun newCorrelationId(): String = "c" + UUID.randomUUID().toString().replace("-", "").take(11)

    // ---- Hop recorders. All no-ops when tracing is off. ----

    fun captured(correlationId: String, meta: CaptureMeta) {
        if (!enabledFlag) return
        val hop = Hop(correlationId, Outcome.CAPTURED, System.currentTimeMillis())
        runCatching { sink.writeCapture(hop, meta) }
    }

    fun parsed(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.PARSED, detail = detail)

    fun deduped(correlationId: String, reason: String, detail: String? = null) =
        record(correlationId, Outcome.DEDUPED, reason = reason, detail = detail)

    /**
     * Every cross-channel merge decision, merged or not. Debug-only, and
     * structural only: channel names and a rule token, never an amount, name or
     * reference.
     */
    fun merged(correlationId: String, reason: String, detail: String? = null) =
        record(correlationId, Outcome.MERGED, reason = reason, detail = detail)

    fun stored(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.STORED, detail = detail)

    fun uploaded(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.UPLOADED, detail = detail)

    fun fcmSent(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.FCM_SENT, detail = detail)

    fun fcmReceived(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.FCM_RECEIVED, detail = detail)

    fun queued(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.QUEUED, detail = detail)

    fun spoken(correlationId: String, detail: String? = null) =
        record(correlationId, Outcome.SPOKEN, detail = detail)

    /**
     * The terminal failure. [reason] must be a stable token so causes can be
     * counted across payments — see `docs/PAYMENT_PIPELINE_FINDINGS.md`.
     */
    fun dropped(correlationId: String, reason: String, detail: String? = null) =
        record(correlationId, Outcome.DROPPED, reason = reason, detail = detail)

    private fun record(
        correlationId: String,
        outcome: Outcome,
        reason: String? = null,
        detail: String? = null,
    ) {
        if (!enabledFlag) return
        val hop = Hop(correlationId, outcome, System.currentTimeMillis(), reason, detail)
        runCatching { sink.write(hop) }
    }

    /**
     * Reduces notification text to a **shape**, with no recoverable content.
     *
     * What survives: character count, token count, and two booleans — whether a
     * currency marker is present, and whether a long digit run (a possible
     * reference/UPI id) is present. What does not survive: any digit, any
     * letter, any name, any amount. So "Rs 500 from Rahul Sharma Upi Ref
     * 512345678901" becomes `len=40 tokens=6 money=1 longdigits=1` — enough to
     * tell "the parser was handed something shaped like a payment" from "it
     * was handed nothing", and impossible to reconstruct a payment from.
     *
     * Applied to *every* piece of captured text, so the raw `captured_notifications`
     * store (which does hold real text, for parser work) is never the only
     * evidence for a lost payment.
     */
    fun maskText(text: String?): String {
        if (text.isNullOrEmpty()) return "empty"
        val tokens = text.split(' ', '\n', '\t').count { it.isNotBlank() }
        val money = MONEY_MARKER.containsMatchIn(text)
        val longDigits = LONG_DIGITS.containsMatchIn(text)
        return "len=${text.length} tokens=$tokens money=${if (money) 1 else 0} " +
            "longdigits=${if (longDigits) 1 else 0}"
    }

    private val MONEY_MARKER = Regex("[₹]|\\b(?:rs|inr)\\b", RegexOption.IGNORE_CASE)
    private val LONG_DIGITS = Regex("\\d{6,}")
}
package com.vivekray898.payvoice.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dedup store (spec §7). One row per announced/persisted payment fingerprint.
 * Retention default 24h, configurable.
 */
@Entity(
    tableName = "processed_events",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        // Retention prunes on announcedAtMs; without this every daily cleanup
        // full-scans the dedup store.
        Index(value = ["announcedAtMs"]),
        // The cross-channel merge candidate lookup is
        // `amountMinor = ? AND postedAtMs BETWEEN ? AND ?`, newest first.
        // Composite, because neither column alone narrows the scan: amounts
        // repeat, and the window is a range not an equality.
        Index(value = ["amountMinor", "postedAtMs"]),
    ],
)
data class ProcessedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val eventId: String,
    val sourcePackage: String,
    val amountMinor: Long,
    val announcedAtMs: Long,
    /**
     * Which channel produced this row. Rule 2 of [com.vivekray898.payvoice.core
     * .dedup.CrossChannelMerger]: two events from the same channel are never
     * merged without a shared reference.
     */
    val captureSource: String = "UNKNOWN",
    /**
     * When the source posted it, not when we announced it. Rule 3 measures the
     * gap between two *source* events; mixing in the announcement latency would
     * make the window mean something slightly different on a slow device.
     */
    val postedAtMs: Long = 0L,
    /**
     * Whether the parser found a UPI/Txn reference. Rule 4: if either side of
     * a cross-channel pair has one, the reference decides — so only the
     * presence is stored, never the reference itself.
     */
    val hasReference: Boolean = false,
)

/**
 * Announcement history shown on the parent home screen (spec §22). Default
 * retention 7 days. Raw notification text is intentionally NOT stored.
 */
@Entity(
    tableName = "announcement_history",
    // Every read is ORDER BY announcedAtMs DESC and retention prunes on the
    // same column; without the index both sort the whole table per query.
    indices = [Index(value = ["announcedAtMs"])],
)
data class AnnouncementEntity(
    @PrimaryKey val eventId: String,
    val fingerprint: String,
    val sourceName: String,
    val amountMinor: Long,
    val currency: String,
    val senderName: String?,
    val announcementText: String,
    val detectedAtMs: Long,
    val announcedAtMs: Long,
    /** Which channel captured it (GPAY_NOTIFICATION / SMS_KOTAK / SMS_BANK). */
    val captureSource: String = "UNKNOWN",
    /** Human-readable parser id for diagnostics. */
    val parserName: String = "unknown",
    /**
     * Set when this row is a *second signal for a payment already announced* —
     * the same amount seen on another channel inside
     * [com.vivekray898.payvoice.core.dedup.CrossChannelMerger.CROSS_CHANNEL_MERGE_WINDOW_MS]
     * — and so was deliberately NOT announced again. It points at the
     * [eventId] that was. Null for every announced payment.
     */
    val mergedWithEventId: String? = null,
)

/**
 * Local-only diagnostic capture of raw notification contents (spec §4).
 * Never uploaded; capped at 50 rows. Captures the standard extras needed to
 * refine parsers on-device (bigText/subText often carry the payment line).
 */
@Entity(
    tableName = "captured_notifications",
    indices = [Index(value = ["capturedAtMs"])],
)
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String? = null,
    val subText: String? = null,
    val notificationId: Int = 0,
    val postedTimeMs: Long = 0,
    val extrasSummary: String? = null,
    val capturedAtMs: Long,
) {
    companion object {
        const val CAP_MAX = 50
    }
}

/**
 * Capped structured diagnostics log (spec §32/§33) — stage timestamps, states,
 * errors. Never logs raw bank content in release builds.
 */
@Entity(
    tableName = "diagnostic_log",
    indices = [Index(value = ["atMs"])],
)
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val tag: String,
    val message: String,
)

/**
 * Debug-only end-to-end trace row (reliability Step 2). One row per hop of one
 * payment; [correlationId] is minted at capture and ties them together.
 *
 * Privacy: no amount, name, number or notification text is ever written here.
 * The optional capture-metadata columns are structural only, and [maskedText]
 * is a shape (length/token counts + two booleans) produced by
 * `PaymentTrace.maskText` — see that function for the guarantee.
 *
 * Rows are only ever inserted by debug builds; the table is harmless empty in
 * release, but it is kept in the schema so the debug UI needs no separate
 * database.
 */
@Entity(
    tableName = "trace_events",
    indices = [
        // The trace screen groups by correlation id and every export/sort is
        // newest-first; both columns are queried constantly.
        Index(value = ["correlationId"]),
        Index(value = ["atMs"]),
    ],
)
data class TraceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val correlationId: String,
    val outcome: String,
    val atMs: Long,
    /** Stable token for DEDUPED/DROPPED causes (countable, never prose). */
    val reason: String? = null,
    val detail: String? = null,
    // ---- Capture metadata (structural, populated on the CAPTURED row only) ----
    val sourcePackage: String? = null,
    val notificationId: Int? = null,
    val notificationTag: String? = null,
    val notificationCategory: String? = null,
    val notificationFlags: Int? = null,
    val postedAtMs: Long? = null,
    val isUpdate: Boolean? = null,
    /** Shape only: `len=.. tokens=.. money=.. longdigits=..`. */
    val maskedText: String? = null,
)

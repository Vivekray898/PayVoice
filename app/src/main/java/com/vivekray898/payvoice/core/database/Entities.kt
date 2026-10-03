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
    ],
)
data class ProcessedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val eventId: String,
    val sourcePackage: String,
    val amountMinor: Long,
    val announcedAtMs: Long,
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

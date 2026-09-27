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
    indices = [Index(value = ["fingerprint"], unique = true)],
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
@Entity(tableName = "announcement_history")
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
)

/**
 * Local-only diagnostic capture of raw notification contents (spec §4).
 * Never uploaded; capped at [CAP_MAX] rows.
 */
@Entity(tableName = "captured_notifications")
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val extrasSummary: String?,
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
@Entity(tableName = "diagnostic_log")
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val tag: String,
    val message: String,
)

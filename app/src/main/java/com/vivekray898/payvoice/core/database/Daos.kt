package com.vivekray898.payvoice.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedEventDao {

    /** INSERT OR IGNORE: duplicate fingerprints are silently dropped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ProcessedEventEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM processed_events WHERE fingerprint = :fingerprint)")
    suspend fun exists(fingerprint: String): Boolean

    @Query("DELETE FROM processed_events WHERE announcedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    /**
     * Cross-channel dedup window (reliability fix): did a BANK-SMS event of
     * [amountMinor] get announced in the last N seconds? Fingerprints of the
     * same payment can differ across channels (GPay wording vs SMS wording,
     * sender name present in one only), so the window is the safety net
     * against double announcements when both channels carry the same payment.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM processed_events " +
            "WHERE sourcePackage LIKE 'SMS%' AND amountMinor = :amountMinor " +
            "AND announcedAtMs >= :sinceMs)"
    )
    suspend fun recentSmsExists(amountMinor: Long, sinceMs: Long): Boolean

    /**
     * Cross-channel counterpart: did a NON-SMS event (GPay notification) of
     * [amountMinor] get announced in the last N seconds? 'remote' rows are
     * excluded — employee-side FCM dedup must never suppress a local
     * capture on the same device.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM processed_events " +
            "WHERE sourcePackage NOT LIKE 'SMS%' AND sourcePackage != 'remote' " +
            "AND amountMinor = :amountMinor AND announcedAtMs >= :sinceMs)"
    )
    suspend fun recentNonSmsExists(amountMinor: Long, sinceMs: Long): Boolean

    @Query("SELECT COUNT(*) FROM processed_events")
    suspend fun count(): Int
}

@Dao
interface AnnouncementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AnnouncementEntity)

    @Query("SELECT * FROM announcement_history ORDER BY announcedAtMs DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcement_history ORDER BY announcedAtMs DESC LIMIT 1")
    suspend fun latest(): AnnouncementEntity?

    @Query("DELETE FROM announcement_history WHERE announcedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}

@Dao
interface CapturedNotificationDao {

    @Insert
    suspend fun insert(entry: CapturedNotificationEntity)

    @Query("SELECT * FROM captured_notifications ORDER BY capturedAtMs DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications ORDER BY capturedAtMs DESC LIMIT 1")
    suspend fun latest(): CapturedNotificationEntity?

    @Query("DELETE FROM captured_notifications")
    suspend fun clear()

    /** Keeps only the newest [CAP_MAX] rows (entity companion constant). */
    @Query(
        "DELETE FROM captured_notifications WHERE id NOT IN " +
            "(SELECT id FROM captured_notifications ORDER BY capturedAtMs DESC LIMIT 50)"
    )
    suspend fun trim()
}

@Dao
interface DiagnosticDao {

    @Insert
    suspend fun insert(entry: DiagnosticEntity)

    @Query("SELECT * FROM diagnostic_log ORDER BY atMs DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<DiagnosticEntity>>

    @Query(
        "DELETE FROM diagnostic_log WHERE id NOT IN " +
            "(SELECT id FROM diagnostic_log ORDER BY atMs DESC LIMIT 300)"
    )
    suspend fun trim()

    @Query("DELETE FROM diagnostic_log")
    suspend fun clear()
}

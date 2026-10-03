package com.vivekray898.payvoice.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProcessedEventEntity::class,
        AnnouncementEntity::class,
        CapturedNotificationEntity::class,
        DiagnosticEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class PayVoiceDatabase : RoomDatabase() {
    abstract fun processedEventDao(): ProcessedEventDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun capturedNotificationDao(): CapturedNotificationDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        /**
         * No destructive fallback: an unmigrated version must fail loudly in
         * development rather than silently wipe payment history in production.
         * WAL is explicit so the announcement write never blocks a history read.
         */
        fun build(context: Context): PayVoiceDatabase =
            Room.databaseBuilder(context, PayVoiceDatabase::class.java, "payvoice.db")
                .addMigrations(*Migrations.ALL)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/**
 * Applies user-configurable retention to all bounded stores. Runs on app start
 * and once per day via WorkManager — the only scheduled work in the app.
 */
class RetentionCleaner(private val db: PayVoiceDatabase) {
    suspend fun clean(dedupHours: Int, historyDays: Int, nowMs: Long = System.currentTimeMillis()) {
        db.processedEventDao().deleteOlderThan(nowMs - dedupHours * 3_600_000L)
        db.announcementDao().deleteOlderThan(nowMs - historyDays * 86_400_000L)
        db.capturedNotificationDao().trim()
        db.diagnosticDao().trim()
    }
}

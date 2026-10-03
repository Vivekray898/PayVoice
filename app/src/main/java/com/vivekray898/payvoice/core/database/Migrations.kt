package com.vivekray898.payvoice.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations for [PayVoiceDatabase].
 *
 * The database previously carried `fallbackToDestructiveMigration(dropAllTables
 * = true)`, which meant every schema bump silently deleted the payment history
 * and the dedup store — a re-delivered `evt_…` would then be announced twice.
 * Every past version is migrated forward here instead, so upgrading never
 * destroys data.
 *
 * Tables that gained NOT NULL columns are rebuilt rather than altered with a
 * DEFAULT clause: `ALTER TABLE … ADD COLUMN … NOT NULL` needs a default, and a
 * lingering default would make Room's schema validation disagree with the
 * entity definition. Copy-and-swap reproduces the exported schema exactly.
 */
object Migrations {

    /** v1 → v2: captured_notifications gained the bigText/subText/notificationId/postedTimeMs extras. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `captured_notifications_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `title` TEXT,
                    `text` TEXT,
                    `bigText` TEXT,
                    `subText` TEXT,
                    `notificationId` INTEGER NOT NULL,
                    `postedTimeMs` INTEGER NOT NULL,
                    `extrasSummary` TEXT,
                    `capturedAtMs` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `captured_notifications_new`
                    (`id`, `packageName`, `title`, `text`, `bigText`, `subText`,
                     `notificationId`, `postedTimeMs`, `extrasSummary`, `capturedAtMs`)
                SELECT `id`, `packageName`, `title`, `text`, NULL, NULL,
                       0, 0, `extrasSummary`, `capturedAtMs`
                FROM `captured_notifications`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `captured_notifications`")
            db.execSQL("ALTER TABLE `captured_notifications_new` RENAME TO `captured_notifications`")
        }
    }

    /** v2 → v3: announcement_history gained captureSource and parserName. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `announcement_history_new` (
                    `eventId` TEXT NOT NULL,
                    `fingerprint` TEXT NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `amountMinor` INTEGER NOT NULL,
                    `currency` TEXT NOT NULL,
                    `senderName` TEXT,
                    `announcementText` TEXT NOT NULL,
                    `detectedAtMs` INTEGER NOT NULL,
                    `announcedAtMs` INTEGER NOT NULL,
                    `captureSource` TEXT NOT NULL,
                    `parserName` TEXT NOT NULL,
                    PRIMARY KEY(`eventId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `announcement_history_new`
                    (`eventId`, `fingerprint`, `sourceName`, `amountMinor`, `currency`,
                     `senderName`, `announcementText`, `detectedAtMs`, `announcedAtMs`,
                     `captureSource`, `parserName`)
                SELECT `eventId`, `fingerprint`, `sourceName`, `amountMinor`, `currency`,
                       `senderName`, `announcementText`, `detectedAtMs`, `announcedAtMs`,
                       'UNKNOWN', 'unknown'
                FROM `announcement_history`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `announcement_history`")
            db.execSQL("ALTER TABLE `announcement_history_new` RENAME TO `announcement_history`")
        }
    }

    /**
     * v3 → v4: indices on the columns the DAOs actually sort and prune by.
     *
     * Every history/diagnostic query is `ORDER BY <timestamp> DESC` and every
     * retention pass is `DELETE … WHERE <timestamp> < cutoff`; without an index
     * SQLite scans the whole table and sorts it, on a low-RAM device, on every
     * single read and every daily cleanup.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_processed_events_announcedAtMs` " +
                    "ON `processed_events` (`announcedAtMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_announcement_history_announcedAtMs` " +
                    "ON `announcement_history` (`announcedAtMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_captured_notifications_capturedAtMs` " +
                    "ON `captured_notifications` (`capturedAtMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_diagnostic_log_atMs` " +
                    "ON `diagnostic_log` (`atMs`)",
            )
        }
    }

    /** Every migration, in ascending order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}

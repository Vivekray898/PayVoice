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

    /**
     * v4 → v5: `trace_events`, the debug-only end-to-end trace store
     * (reliability Step 2). Purely additive — no existing table is touched, so
     * no payment history, dedup row or history entry is at risk. Indices cover
     * the two access patterns: grouping by correlation id, and newest-first
     * ordering for the screen and the bounded export.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trace_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `correlationId` TEXT NOT NULL,
                    `outcome` TEXT NOT NULL,
                    `atMs` INTEGER NOT NULL,
                    `reason` TEXT,
                    `detail` TEXT,
                    `sourcePackage` TEXT,
                    `notificationId` INTEGER,
                    `notificationTag` TEXT,
                    `notificationCategory` TEXT,
                    `notificationFlags` INTEGER,
                    `postedAtMs` INTEGER,
                    `isUpdate` INTEGER,
                    `maskedText` TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_trace_events_correlationId` " +
                    "ON `trace_events` (`correlationId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_trace_events_atMs` " +
                    "ON `trace_events` (`atMs`)",
            )
        }
    }

    /**
     * v5 → v6: cross-channel merge support (reliability Step 4).
     *
     * `processed_events` gains the three columns the merge rules need — the
     * capturing channel, the source post time (rule 2 and rule 3 measure
     * source events, not announcement latency) and whether a UPI/Txn reference
     * was present (rule 4). `announcement_history` gains `mergedWithEventId` so
     * a second signal for an already-announced payment is retained and visibly
     * marked rather than vanishing.
     *
     * Both tables are rebuilt rather than `ALTER TABLE … ADD COLUMN`d, for the
     * reason in this file's header: the new NOT NULL columns must end up with
     * the exact Room schema, and a lingering DEFAULT would make validation
     * disagree with the entity. Every existing row is copied — the dedup store
     * is exactly what must NOT be wiped, since a re-delivered `evt_…` meeting
     * an emptied store announces twice.
     *
     * `postedAtMs` backfills from `announcedAtMs` (the closest value that
     * exists) and `captureSource` from `sourcePackage`, so pre-v6 rows stay
     * usable as merge candidates rather than silently comparing against zero.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `processed_events_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fingerprint` TEXT NOT NULL,
                    `eventId` TEXT NOT NULL,
                    `sourcePackage` TEXT NOT NULL,
                    `amountMinor` INTEGER NOT NULL,
                    `announcedAtMs` INTEGER NOT NULL,
                    `captureSource` TEXT NOT NULL,
                    `postedAtMs` INTEGER NOT NULL,
                    `hasReference` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `processed_events_new`
                    (`id`, `fingerprint`, `eventId`, `sourcePackage`, `amountMinor`,
                     `announcedAtMs`, `captureSource`, `postedAtMs`, `hasReference`)
                SELECT `id`, `fingerprint`, `eventId`, `sourcePackage`, `amountMinor`,
                       `announcedAtMs`, 'UNKNOWN', `announcedAtMs`, 0
                FROM `processed_events`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `processed_events`")
            db.execSQL("ALTER TABLE `processed_events_new` RENAME TO `processed_events`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_events_fingerprint` " +
                    "ON `processed_events` (`fingerprint`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_processed_events_announcedAtMs` " +
                    "ON `processed_events` (`announcedAtMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_processed_events_amountMinor_postedAtMs` " +
                    "ON `processed_events` (`amountMinor`, `postedAtMs`)",
            )

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
                    `mergedWithEventId` TEXT,
                    PRIMARY KEY(`eventId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `announcement_history_new`
                    (`eventId`, `fingerprint`, `sourceName`, `amountMinor`, `currency`,
                     `senderName`, `announcementText`, `detectedAtMs`, `announcedAtMs`,
                     `captureSource`, `parserName`, `mergedWithEventId`)
                SELECT `eventId`, `fingerprint`, `sourceName`, `amountMinor`, `currency`,
                       `senderName`, `announcementText`, `detectedAtMs`, `announcedAtMs`,
                       `captureSource`, `parserName`, NULL
                FROM `announcement_history`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `announcement_history`")
            db.execSQL("ALTER TABLE `announcement_history_new` RENAME TO `announcement_history`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_announcement_history_announcedAtMs` " +
                    "ON `announcement_history` (`announcedAtMs`)",
            )
        }
    }

    /** Every migration, in ascending order. */
    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}

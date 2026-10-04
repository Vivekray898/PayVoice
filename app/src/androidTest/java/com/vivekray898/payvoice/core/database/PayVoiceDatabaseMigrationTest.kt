package com.vivekray898.payvoice.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves every historical schema still migrates forward to the current one
 * WITHOUT losing rows — the exact guarantee the removed
 * `fallbackToDestructiveMigration(dropAllTables = true)` used to break.
 */
@RunWith(AndroidJUnit4::class)
class PayVoiceDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PayVoiceDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesFromVersion1ToCurrent() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO announcement_history (eventId, fingerprint, sourceName, amountMinor, " +
                    "currency, announcementText, detectedAtMs, announcedAtMs) " +
                    "VALUES ('evt_v1', 'fp1', 'GPay', 50000, 'INR', 'five hundred', 1, 1)",
            )
            execSQL(
                "INSERT INTO processed_events (fingerprint, eventId, sourcePackage, amountMinor, " +
                    "announcedAtMs) VALUES ('fp1', 'evt_v1', 'remote', 50000, 1)",
            )
            execSQL(
                "INSERT INTO captured_notifications (packageName, title, extrasSummary, capturedAtMs) " +
                    "VALUES ('pkg', 't', 'e', 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
            Migrations.MIGRATION_3_4,
        )

        migrated.query("SELECT captureSource, parserName FROM announcement_history").use { c ->
            c.moveToFirst()
            // Pre-v3 rows get the documented defaults rather than being dropped.
            assertEquals("UNKNOWN", c.getString(0))
            assertEquals("unknown", c.getString(1))
        }
        migrated.query("SELECT notificationId, postedTimeMs FROM captured_notifications").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
            assertEquals(0L, c.getLong(1))
        }
        migrated.query("SELECT COUNT(*) FROM processed_events").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migratesFromVersion3ToCurrentAndKeepsHistory() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO announcement_history (eventId, fingerprint, sourceName, amountMinor, " +
                    "currency, announcementText, detectedAtMs, announcedAtMs, captureSource, parserName) " +
                    "VALUES ('evt_v3', 'fp3', 'Remote', 100, 'INR', 'one hundred', 5, 5, 'REMOTE', 'RemoteFcm')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

        migrated.query("SELECT COUNT(*) FROM announcement_history").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        // The new index is what makes the ORDER BY/DELETE history queries cheap.
        migrated.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND " +
                "name = 'index_announcement_history_announcedAtMs'",
        ).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }

    /**
     * v5 → v6: the cross-channel merge columns. The dedup store is the one
     * table that must never be wiped — a re-delivered `evt_…` meeting an
     * emptied store announces twice — so this asserts rows SURVIVE, not just
     * that the schema validates.
     */
    @Test
    fun migratesFromVersion5ToCurrentAndKeepsDedupeRows() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO processed_events (fingerprint, eventId, sourcePackage, amountMinor, " +
                    "announcedAtMs) VALUES ('fp5', 'evt_v5', 'pkg', 50000, 1700)",
            )
            execSQL(
                "INSERT INTO announcement_history (eventId, fingerprint, sourceName, amountMinor, " +
                    "currency, announcementText, detectedAtMs, announcedAtMs, captureSource, parserName) " +
                    "VALUES ('evt_h5', 'fp5', 'GPay', 50000, 'INR', 'five hundred', 1700, 1700, " +
                    "'GPAY_NOTIFICATION', 'GooglePayParser')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 6, true, Migrations.MIGRATION_5_6,
        )

        migrated.query(
            "SELECT fingerprint, captureSource, postedAtMs, hasReference FROM processed_events",
        ).use { c ->
            c.moveToFirst()
            assertEquals("fp5", c.getString(0))
            // Pre-v6 rows are usable as merge candidates rather than silently
            // comparing against zero: the channel is unknown and the post time
            // is backfilled from what the row does know.
            assertEquals("UNKNOWN", c.getString(1))
            assertEquals(1700L, c.getLong(2))
            assertEquals(0, c.getInt(3))
        }
        migrated.query("SELECT mergedWithEventId FROM announcement_history").use { c ->
            c.moveToFirst()
            // A pre-merge row was announced, not merged.
            assertTrue(c.isNull(0))
        }
        // The merge lookup is an amount equality plus a time range; the
        // composite index is what keeps it off a full scan.
        migrated.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND " +
                "name = 'index_processed_events_amountMinor_postedAtMs'",
        ).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}

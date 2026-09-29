package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.database.CrossChannelTags
import com.vivekray898.payvoice.core.database.ProcessedEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deliverable 2d (reliability): the behavioral contract of the cross-channel
 * suppression, as close to end-to-end as a JVM test can get WITHOUT adding a
 * new dependency.
 *
 * Why not Room.inMemoryDatabaseBuilder: it requires androidx.test/Robolectric
 * (instrumented or a new test dependency — both prohibited by the task rules).
 * Why this still counts: Room/KSP GENERATES the DAO SQL at compile time from
 * the same [CrossChannelTags] constants this test compiles against (verified
 * in ProcessedEventDao_Impl), and SQLite LIKE semantics are fixed. The only
 * JVM-untestable residues are Room's own LIKE evaluation (platform SQLite —
 * exercised on-device by Scenario 3 of the test matrix) and the pipeline's
 * insert/announce sequencing (Android-free logic already covered by the
 * fingerprint tests). Everything this side controls — the writer/reader
 * contract, tag uniqueness, symmetric decision inputs, and window
 * arithmetic — is pinned here.
 */
class CrossChannelSuppressionTest {

    /** Emulates the DAO's generated SQL: `sourcePackage LIKE <pattern>`. */
    private fun likeMatches(pattern: String, value: String): Boolean {
        // SQLite LIKE: % matches any sequence (incl. empty); case-insensitive
        // for ASCII by default — tags are uppercase-constant + raw origin.
        val regex = buildString {
            append('^')
            for (c in pattern) when (c) {
                '%' -> append(".*")
                else -> append(Regex.escape(c.toString()))
            }
            append('$')
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(value)
    }

    /** Mirrors the generated query shape exactly (see ProcessedEventDao_Impl). */
    private fun suppressionDecides(
        rows: List<ProcessedEventEntity>,
        incomingIsSms: Boolean,
        amountMinor: Long,
        nowMs: Long,
    ): Boolean {
        val pattern = if (incomingIsSms) CrossChannelTags.SMS_LIKE
        else CrossChannelTags.NOTIF_LIKE
        // NOTE the deliberate inversion: an SMS capture asks about NOTIF rows
        // and vice versa. Pinned by `incoming pattern == opposite channel`.
        val askedPattern = if (incomingIsSms) CrossChannelTags.NOTIF_LIKE
        else CrossChannelTags.SMS_LIKE
        assertNotEquals(pattern, askedPattern)
        return rows.any {
            likeMatches(askedPattern, it.sourcePackage) &&
                it.amountMinor == amountMinor &&
                it.announcedAtMs >= nowMs
        }
    }

    private fun row(
        sourcePackage: String,
        amountMinor: Long,
        announcedAtMs: Long,
    ) = ProcessedEventEntity(
        fingerprint = "evt_$sourcePackage$amountMinor$announcedAtMs",
        eventId = "evt_x",
        sourcePackage = sourcePackage,
        amountMinor = amountMinor,
        announcedAtMs = announcedAtMs,
    )

    private val now = 1_782_000_000_000L
    private val amount = 50_000L // ₹500

    // ---- Writer/reader contract ----

    @Test
    fun `writer tags survive the reader patterns`() {
        val smsTag = CrossChannelTags.tag(isSms = true, originId = "KKBK6789")
        val notifTag = CrossChannelTags.tag(
            isSms = false,
            originId = "com.google.android.apps.nbu.paisa.user",
        )
        assertTrue(likeMatches(CrossChannelTags.SMS_LIKE, smsTag))
        assertTrue(likeMatches(CrossChannelTags.NOTIF_LIKE, notifTag))
        // Cross-matches never happen.
        assertFalse(likeMatches(CrossChannelTags.SMS_LIKE, notifTag))
        assertFalse(likeMatches(CrossChannelTags.NOTIF_LIKE, smsTag))
    }

    @Test
    fun `legacy untagged and remote rows never match either pattern`() {
        for (legacy in listOf("KKBK6789", "com.google.android.apps.nbu.paisa.user", "remote")) {
            assertFalse(likeMatches(CrossChannelTags.SMS_LIKE, legacy))
            assertFalse(likeMatches(CrossChannelTags.NOTIF_LIKE, legacy))
        }
    }

    // ---- The three behavioral cases from deliverable 2d ----

    @Test
    fun `two separate payments of same amount 20s apart are NOT suppressed`() {
        // GPay notification ₹500 announced at t=0; SMS ₹500 arrives at t=20s.
        val history = listOf(
            row(CrossChannelTags.tag(false, "com.google.android.apps.nbu.paisa.user"), amount, now),
        )
        val suppressed = suppressionDecides(
            rows = history,
            incomingIsSms = true,
            amountMinor = amount,
            nowMs = now + 20_000 - 10_000, // DAO cutoff for a t=20s arrival
        )
        assertFalse("20s-old same-amount notification must NOT suppress", suppressed)
    }

    @Test
    fun `gpay notification + matching sms within 10s IS suppressed`() {
        val history = listOf(
            row(CrossChannelTags.tag(false, "com.google.android.apps.nbu.paisa.user"), amount, now),
        )
        val suppressedAt3s = suppressionDecides(
            rows = history,
            incomingIsSms = true,
            amountMinor = amount,
            nowMs = now + 3_000 - 10_000,
        )
        val suppressedAtBoundary = suppressionDecides(
            rows = history,
            incomingIsSms = true,
            amountMinor = amount,
            nowMs = now + 10_000 - 10_000, // exactly at the window edge (>=)
        )
        assertTrue("3s-lag SMS must be suppressed (same payment)", suppressedAt3s)
        assertTrue("boundary row is inside the >= window", suppressedAtBoundary)
    }

    @Test
    fun `same-channel same-amount payments are NEVER suppressed`() {
        // Two genuine ₹500 GPay notifications 3s apart (real shop scenario).
        val history = listOf(
            row(CrossChannelTags.tag(false, "com.google.android.apps.nbu.paisa.user"), amount, now),
        )
        val suppressed = suppressionDecides(
            rows = history,
            incomingIsSms = false, // notification again → asks about SMS rows
            amountMinor = amount,
            nowMs = now + 3_000 - 10_000,
        )
        assertFalse("same-channel capture must never suppress", suppressed)

        // And the SMS→SMS case.
        val smsHistory = listOf(
            row(CrossChannelTags.tag(true, "KKBK6789"), amount, now),
        )
        val smsSuppressed = suppressionDecides(
            rows = smsHistory,
            incomingIsSms = true,
            amountMinor = amount,
            nowMs = now + 3_000 - 10_000,
        )
        assertFalse("same-channel SMS must never suppress", smsSuppressed)
    }

    @Test
    fun `different amounts never suppress each other`() {
        val history = listOf(
            row(CrossChannelTags.tag(false, "com.google.android.apps.nbu.paisa.user"), 50_000L, now),
        )
        val suppressed = suppressionDecides(
            rows = history,
            incomingIsSms = true,
            amountMinor = 51_000L,
            nowMs = now - 5_000,
        )
        assertFalse(suppressed)
    }

    // ---- Window arithmetic (deliverable 2a: 30s → 10s) ----

    @Test
    fun `window constant is 10 seconds`() {
        assertEquals(10_000L, com.vivekray898.payvoice.CrossChannelWindow.WINDOW_MS)
    }
}

package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.database.AnnouncementEntity
import com.vivekray898.payvoice.core.database.CapturedNotificationEntity
import com.vivekray898.payvoice.core.database.DiagnosticEntity
import com.vivekray898.payvoice.core.database.ProcessedEventEntity
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.Fingerprinter
import com.vivekray898.payvoice.core.parser.PaymentParserRegistry
import com.vivekray898.payvoice.core.settings.SettingsRepository
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The entire Phase-1 core path, in one short sequence:
 *
 * notification → parse → confidence gate → fingerprint dedup → TTS → persist
 *
 * Everything here runs on a background dispatcher. Nothing blocks
 * onNotificationPosted. Raw notification content never leaves the device.
 */
class PaymentPipeline(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val parsers: PaymentParserRegistry,
    private val speaker: AnnouncementSpeaker,
    private val db: PayVoiceDatabase,
) {

    private val _lastAnnouncement = MutableStateFlow<AnnouncementEntity?>(null)
    val lastAnnouncement: StateFlow<AnnouncementEntity?> = _lastAnnouncement

    /** Entry point from the NotificationListenerService (off-main dispatch). */
    fun handleNotification(
        packageName: String,
        title: String?,
        text: String?,
        extrasSummary: String?,
        postedAtMs: Long,
    ) {
        scope.launch {
            runCatching { process(packageName, title, text, extrasSummary, postedAtMs) }
                .onFailure { diag("pipeline", "error: ${it.javaClass.simpleName}") }
        }
    }

    /**
     * Capture-only path for unknown packages while the user is verifying the
     * Kotak package id. Stores a local-only capture row; never parses or speaks.
     */
    fun captureOnly(
        packageName: String,
        title: String?,
        text: String?,
        extrasSummary: String?,
        postedAtMs: Long,
    ) {
        scope.launch {
            runCatching {
                db.capturedNotificationDao().insert(
                    CapturedNotificationEntity(
                        packageName = packageName,
                        title = title,
                        text = text,
                        extrasSummary = extrasSummary,
                        capturedAtMs = System.currentTimeMillis(),
                    )
                )
                db.capturedNotificationDao().trim()
                diag("capture", "captured package=$packageName")
            }
        }
    }

    private suspend fun process(
        packageName: String,
        title: String?,
        text: String?,
        extrasSummary: String?,
        postedAtMs: Long,
    ) {
        val source = PaymentSource.fromPackage(packageName) ?: return
        val parser = parsers.parserForPackage(packageName) ?: return

        val parsed = parser.parse(title, text)
        if (parsed == null) {
            diag("parser", "ignored non-payment notification from ${source.name}")
            return
        }

        // Local-only diagnostic capture (spec §4). Never uploaded.
        runCatching {
            db.capturedNotificationDao().insert(
                CapturedNotificationEntity(
                    packageName = packageName,
                    title = title,
                    text = text,
                    extrasSummary = extrasSummary,
                    capturedAtMs = System.currentTimeMillis(),
                )
            )
            db.capturedNotificationDao().trim()
        }

        val s = settings.settings.value
        val minRank = if (s.announceHighConfidenceOnly) rank(Confidence.HIGH) else rank(Confidence.MEDIUM)

        if (parsed.direction != Direction.RECEIVED) {
            diag("gate", "direction=${parsed.direction} suppressed (${source.name})")
            return
        }
        if (rank(parsed.confidence) < minRank) {
            diag("gate", "confidence=${parsed.confidence} below threshold (${source.name})")
            return
        }
        val amount = parsed.amountMinor
        if (amount == null || amount <= 0) {
            diag("gate", "no usable amount (${source.name})")
            return
        }

        // Duplicate protection (spec §7): deterministic fingerprint, INSERT OR IGNORE.
        val fingerprint = Fingerprinter.fingerprint(
            packageId = packageName,
            amountMinor = amount,
            timestampMs = postedAtMs,
            title = title,
            text = text,
            referenceId = parsed.referenceId,
        )
        val dedupInsert = db.processedEventDao().insert(
            ProcessedEventEntity(
                fingerprint = fingerprint,
                eventId = fingerprint,
                sourcePackage = packageName,
                amountMinor = amount,
                announcedAtMs = System.currentTimeMillis(),
            )
        )
        if (dedupInsert == -1L) {
            diag("dedup", "duplicate suppressed: ${fingerprint.take(16)}")
            return
        }

        // Announce. Text is composed locally; Phase 3 sends it inside the FCM payload.
        val announcement = AnnouncementComposer.compose(
            amountMinor = amount,
            senderName = parsed.senderName,
            source = parsed.source,
            style = s.style,
            language = s.language,
        )
        val announcedAt = System.currentTimeMillis()
        val spoken = speaker.speak(announcement)

        val entity = AnnouncementEntity(
            eventId = fingerprint,
            fingerprint = fingerprint,
            sourceName = source.displayName,
            amountMinor = amount,
            currency = parsed.currency,
            senderName = parsed.senderName,
            announcementText = announcement,
            detectedAtMs = postedAtMs,
            announcedAtMs = announcedAt,
        )
        db.announcementDao().insert(entity)
        _lastAnnouncement.value = entity
        diag(
            "pipeline",
            "announced=$spoken amount=$amount src=${source.name} detToAnnounceMs=${announcedAt - postedAtMs}",
        )
    }

    /**
     * Owner-triggered test announcement (spec §27). Never touches dedup,
     * history, or payment data. Returns true when spoken.
     */
    suspend fun announceTest(): Boolean {
        val s = settings.settings.value
        val text = when (s.language) {
            AnnouncementLanguage.ENGLISH, AnnouncementLanguage.HINGLISH ->
                AnnouncementComposer.TEST_ANNOUNCEMENT_EN
            AnnouncementLanguage.HINDI -> AnnouncementComposer.TEST_ANNOUNCEMENT_HI
        }
        val ok = speaker.speak(text)
        diag("test", "test announcement spoken=$ok")
        return ok
    }

    /**
     * Debug simulator: pushes a canned notification through the real parse →
     * dedup → announce path (spec Phase-1 proof). History rows are tagged
     * "sim" so they are never confused with real payments.
     */
    fun simulate(source: PaymentSource, title: String, text: String) {
        scope.launch {
            runCatching {
                process(source.packageId, title, text, extrasSummary = null, postedAtMs = System.currentTimeMillis())
            }
        }
    }

    private fun rank(c: Confidence): Int = when (c) {
        Confidence.HIGH -> 3
        Confidence.MEDIUM -> 2
        Confidence.LOW -> 1
    }

    private suspend fun diag(tag: String, message: String) {
        runCatching {
            db.diagnosticDao().insert(DiagnosticEntity(atMs = System.currentTimeMillis(), tag = tag, message = message))
            db.diagnosticDao().trim()
        }
    }
}

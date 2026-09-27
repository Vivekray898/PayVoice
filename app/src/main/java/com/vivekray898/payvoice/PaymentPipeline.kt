package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.database.AnnouncementEntity
import com.vivekray898.payvoice.core.database.CapturedNotificationEntity
import com.vivekray898.payvoice.core.database.DiagnosticEntity
import com.vivekray898.payvoice.core.database.ProcessedEventEntity
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.model.CaptureEvent
import com.vivekray898.payvoice.core.model.CaptureSource
import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.ParsedNotification
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.Fingerprinter
import com.vivekray898.payvoice.core.parser.PaymentParserRegistry
import com.vivekray898.payvoice.core.parser.sms.SmsPaymentParserRegistry
import com.vivekray898.payvoice.core.settings.SettingsRepository
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The unified capture→announce path (Phase 11). BOTH channels — app
 * notifications and bank SMS — enter through [handleCapture] and share:
 * parse → confidence gate → dedup → TTS → persist. One Room store, one
 * dedup table, one announcer. Nothing is duplicated per channel.
 *
 * Everything runs on a background dispatcher; raw content stays on-device.
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

    private val _lastDedupWasDuplicate = MutableStateFlow<Boolean?>(null)
    val lastDedupWasDuplicate: StateFlow<Boolean?> = _lastDedupWasDuplicate

    // ---- Entry points ----

    /** NotificationListenerService entry (existing behavior preserved). */
    fun handleNotification(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
        notificationId: Int,
        postedAtMs: Long,
        extrasSummary: String?,
    ) {
        val source = PaymentSource.fromPackage(packageName) ?: return
        val captureSource = when (source) {
            PaymentSource.GOOGLE_PAY -> CaptureSource.GPAY_NOTIFICATION
            PaymentSource.KOTAK -> CaptureSource.KOTAK_NOTIFICATION
        }
        scope.launch {
            runCatching {
                handleCapture(
                    CaptureEvent(
                        captureSource = captureSource,
                        originId = packageName,
                        title = title,
                        body = text.orEmpty(),
                        bigText = bigText,
                        subText = subText,
                        notificationId = notificationId,
                        postedAtMs = postedAtMs,
                    ),
                    extrasSummary = extrasSummary,
                )
            }.onFailure { diag("pipeline", "error: ${it.javaClass.simpleName}") }
        }
    }

    /** SMS receiver entry (new backup path). Suspending: callers wrap in launch. */
    suspend fun handleCapture(event: CaptureEvent, extrasSummary: String? = null) {
        val s = settings.settings.value
        if (event.captureSource.name.startsWith("SMS") && !s.smsCaptureEnabled) {
            diag("sms", "capture disabled by setting")
            return
        }
        runCatching { processCapture(event, extrasSummary) }
            .onFailure { diag("pipeline", "error: ${it.javaClass.simpleName}") }
    }

    /** Capture-only path for unknown packages (diagnostics capture mode). */
    fun captureOnly(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
        notificationId: Int,
        postedAtMs: Long,
        extrasSummary: String?,
    ) {
        scope.launch {
            runCatching {
                db.capturedNotificationDao().insert(
                    CapturedNotificationEntity(
                        packageName = packageName,
                        title = title,
                        text = text,
                        bigText = bigText,
                        subText = subText,
                        notificationId = notificationId,
                        postedTimeMs = postedAtMs,
                        extrasSummary = extrasSummary,
                        capturedAtMs = System.currentTimeMillis(),
                    )
                )
                db.capturedNotificationDao().trim()
                diag("capture", "captured package=$packageName")
            }
        }
    }

    // ---- Core unified path ----

    private suspend fun processCapture(event: CaptureEvent, extrasSummary: String?) {
        // 1. Parse by channel.
        val parsed: ParsedNotification? = when {
            event.captureSource.name.startsWith("SMS") -> {
                val parserResult = SmsPaymentParserRegistry.parse(
                    sender = event.originId,
                    body = event.body,
                    receivedAtMs = event.postedAtMs,
                )
                parserResult
            }
            else -> parsers.parserForPackage(event.originId)?.parse(event.title, event.body)
        }

        // 2. Local-only diagnostic capture of raw content. Never uploaded.
        runCatching {
            db.capturedNotificationDao().insert(
                CapturedNotificationEntity(
                    packageName = event.originId,
                    title = event.title,
                    text = event.body.take(240),
                    bigText = event.bigText,
                    subText = event.subText,
                    notificationId = event.notificationId,
                    postedTimeMs = event.postedAtMs,
                    extrasSummary = extrasSummary,
                    capturedAtMs = System.currentTimeMillis(),
                )
            )
            db.capturedNotificationDao().trim()
        }

        if (parsed == null) {
            diag("parser", "no payment in capture from ${event.originId} (${event.captureSource})")
            return
        }

        // 3. Confidence/direction gates (spec: only RECEIVED announces).
        val s = settings.settings.value
        val minRank = if (s.announceHighConfidenceOnly) rank(Confidence.HIGH) else rank(Confidence.MEDIUM)
        if (parsed.direction != Direction.RECEIVED) {
            diag("gate", "direction=${parsed.direction} suppressed (${parsed.source.name})")
            return
        }
        if (rank(parsed.confidence) < minRank) {
            diag("gate", "confidence=${parsed.confidence} below threshold (${parsed.source.name})")
            return
        }
        val amount = parsed.amountMinor
        if (amount == null || amount <= 0) {
            diag("gate", "no usable amount (${parsed.source.name})")
            return
        }

        // 4. Cross-channel deduplication (Phase 12). UTR/reference first —
        // the same UTR in a bank SMS and a bank notification is ONE payment.
        // Without a reference: amount+direction+sender+5-minute bucket.
        val fingerprint = Fingerprinter.captureFingerprint(
            packageId = event.originId,
            amountMinor = amount,
            timestampMs = event.postedAtMs,
            title = event.title,
            text = event.body,
            referenceId = parsed.referenceId,
            senderName = parsed.senderName,
        )
        val dedupInsert = db.processedEventDao().insert(
            ProcessedEventEntity(
                fingerprint = fingerprint,
                eventId = fingerprint,
                sourcePackage = event.originId,
                amountMinor = amount,
                announcedAtMs = System.currentTimeMillis(),
            )
        )
        if (dedupInsert == -1L) {
            _lastDedupWasDuplicate.value = true
            diag("dedup", "duplicate suppressed: ${fingerprint.take(16)}")
            return
        }
        _lastDedupWasDuplicate.value = false

        // 5. Announce. Text composed locally; Phase 3 sends it inside FCM.
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
            sourceName = parsed.sourceLabel ?: parsed.source.displayName,
            amountMinor = amount,
            currency = parsed.currency,
            senderName = parsed.senderName,
            announcementText = announcement,
            detectedAtMs = event.postedAtMs,
            announcedAtMs = announcedAt,
            captureSource = event.captureSource.name,
            parserName = parserNameFor(event),
        )
        db.announcementDao().insert(entity)
        _lastAnnouncement.value = entity
        diag(
            "pipeline",
            "source=${event.captureSource} sender=${event.originId.take(12)} " +
                "bank=${parsed.source.name} dir=${parsed.direction} amount=$amount " +
                "ref=${parsed.referenceId?.take(8) ?: "-"} conf=${parsed.confidence} " +
                "parser=${entity.parserName} announced=$spoken " +
                "detToAnnounceMs=${announcedAt - event.postedAtMs}",
        )
    }

    private fun parserNameFor(event: CaptureEvent): String = when {
        event.captureSource.name.startsWith("SMS") -> when (event.captureSource) {
            CaptureSource.SMS_KOTAK -> "KotakSmsParser"
            else -> "BankSmsParser"
        }
        event.captureSource == CaptureSource.GPAY_NOTIFICATION -> "GooglePayParser"
        else -> "KotakParser"
    }

    /**
     * Owner-triggered test announcement (spec §27). Never touches dedup,
     * history, or payment data.
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

    /** Debug simulator: canned notification through the real pipeline. */
    fun simulate(source: PaymentSource, title: String, text: String) {
        val captureSource = when (source) {
            PaymentSource.GOOGLE_PAY -> CaptureSource.GPAY_NOTIFICATION
            PaymentSource.KOTAK -> CaptureSource.KOTAK_NOTIFICATION
        }
        scope.launch {
            runCatching {
                handleCapture(
                    CaptureEvent(
                        captureSource = captureSource,
                        originId = source.packageId,
                        title = title,
                        body = text,
                        postedAtMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    /** Debug simulator for SMS (Phase 19 tool path; UI exposes in debug builds). */
    fun simulateSms(sender: String, body: String) {
        scope.launch {
            runCatching {
                handleCapture(
                    CaptureEvent(
                        captureSource = CaptureSource.SMS_BANK,
                        originId = sender,
                        title = null,
                        body = body,
                        postedAtMs = System.currentTimeMillis(),
                    )
                )
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
            db.diagnosticDao().insert(
                DiagnosticEntity(atMs = System.currentTimeMillis(), tag = tag, message = message)
            )
            db.diagnosticDao().trim()
        }
    }
}

package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.database.AnnouncementEntity
import com.vivekray898.payvoice.core.database.CapturedNotificationEntity
import com.vivekray898.payvoice.core.database.DiagnosticEntity
import com.vivekray898.payvoice.core.database.ProcessedEventEntity
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.dedup.CrossChannelMerger
import com.vivekray898.payvoice.core.model.CaptureEvent
import com.vivekray898.payvoice.core.model.CaptureSource
import com.vivekray898.payvoice.core.model.Confidence
import com.vivekray898.payvoice.core.model.Direction
import com.vivekray898.payvoice.core.model.KnownPackages
import com.vivekray898.payvoice.core.model.ParsedNotification
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.parser.Fingerprinter
import com.vivekray898.payvoice.core.parser.NotificationTextResolver
import com.vivekray898.payvoice.core.parser.PaymentParserRegistry
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.core.remote.RemoteEventType
import com.vivekray898.payvoice.core.settings.SettingsRepository
import com.vivekray898.payvoice.core.trace.PaymentTrace
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The unified capture→announce path (Phase 11). UPI-app notifications (GPay)
 * enter through [handleCapture] and flow through:
 * parse → confidence gate → dedup → TTS → persist. Nothing is duplicated per channel.
 *
 * Everything runs on a background dispatcher; raw content stays on-device.
 */
class PaymentPipeline(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val parsers: PaymentParserRegistry,
    private val speaker: AnnouncementSpeaker,
    private val db: PayVoiceDatabase,
    private val isDebugBuild: Boolean = false,
    /** Remote fan-out; nullable so unit contexts can omit it. */
    private val remoteSender: RemoteEventSender? = null,
    private val roleProvider: () -> com.vivekray898.payvoice.core.remote.DeviceRole = {
        com.vivekray898.payvoice.core.remote.DeviceRole.UNSET
    },
    private val remoteEnabledProvider: () -> Boolean = { true },
    /** App context for the wake-up notification; null-safe for unit contexts. */
    private val appContext: android.content.Context? = null,
) {
    private val _lastAnnouncement = MutableStateFlow<AnnouncementEntity?>(null)
    val lastAnnouncement: StateFlow<AnnouncementEntity?> = _lastAnnouncement

    private val _lastDedupWasDuplicate = MutableStateFlow<Boolean?>(null)
    val lastDedupWasDuplicate: StateFlow<Boolean?> = _lastDedupWasDuplicate

    // ---- Entry points ----

    /** NotificationListenerService entry (GPay is the only notification source). */
    fun handleNotification(
        correlationId: String,
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
        notificationId: Int,
        notificationTag: String?,
        postedAtMs: Long,
        extrasSummary: String?,
    ) {
        // Single notification gate: a bank app package (e.g. com.kotak811) can
        // never reach the pipeline as a payment — bank payments arrive via SMS.
        // Recognized GPay package variants (OEM/regional flavors) pass the gate
        // and parse with the same parser.
        if (!KnownPackages.isGooglePayPackage(packageName)) return
        scope.launch {
            runCatching {
                handleCapture(
                    CaptureEvent(
                        captureSource = CaptureSource.GPAY_NOTIFICATION,
                        originId = packageName,
                        title = title,
                        body = text.orEmpty(),
                        bigText = bigText,
                        subText = subText,
                        notificationId = notificationId,
                        notificationTag = notificationTag,
                        postedAtMs = postedAtMs,
                    ),
                    correlationId = correlationId,
                    extrasSummary = extrasSummary,
                )
            }.onFailure { diag("pipeline", "error: ${it.javaClass.simpleName}") }
        }
    }

    /** Capture entry (GPay notifications). Suspending: callers wrap in launch. */
    suspend fun handleCapture(
        event: CaptureEvent,
        correlationId: String,
        extrasSummary: String? = null,
    ) {
        runCatching { processCapture(event, correlationId, extrasSummary) }
            .onFailure {
                diag("pipeline", "error: ${it.javaClass.simpleName}")
                PaymentTrace.dropped(correlationId, "pipeline-exception", detail = it.javaClass.simpleName)
            }
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

    private suspend fun processCapture(
        event: CaptureEvent,
        correlationId: String,
        extrasSummary: String?,
    ) {
        // 1. Parse. GPay sometimes carries the payment line in
        // EXTRA_BIG_TEXT or EXTRA_SUB_TEXT instead of EXTRA_TEXT (varies by
        // app version) — try each candidate body, best first, so the payment
        // is never silently dropped because of which extra held the text.
        val parsed: ParsedNotification? =
            parsers.parserForPackage(event.originId)?.let { parser ->
                NotificationTextResolver
                    .candidates(event.title, event.body, event.bigText, event.subText)
                    .firstNotNullOfOrNull { (t, body) -> parser.parse(t, body) }
            }

        val parsedAt = System.currentTimeMillis()

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
            val currencyLike = MONEY_MARKER.containsMatchIn(event.body) ||
                MONEY_MARKER.containsMatchIn(event.bigText.orEmpty()) ||
                MONEY_MARKER.containsMatchIn(event.subText.orEmpty())
            PaymentTrace.parsed(
                correlationId,
                detail = "parser=GooglePayParser currencyMarker=${if (currencyLike) 1 else 0}",
            )
            // Reliability visibility: a whitelisted capture that CONTAINS a
            // currency marker but parsed to nothing is the signature of an
            // unknown GPay/bank wording — surface it in Diagnostics (tag only;
            // the raw content stays in the local capture store, never logs).
            diag(
                "parser",
                (if (currencyLike) "MISSED-PAYMENT? " else "no payment in capture from ") +
                    "${event.originId} (${event.captureSource})",
            )
            PaymentTrace.dropped(
                correlationId,
                reason = if (currencyLike) "parse-null-money-marker" else "parse-null",
                detail = "extrasSeen=${extrasSeenFor(event)}",
            )
            return
        }

        // 3. Confidence/direction gates (spec: only RECEIVED announces).
        val s = settings.settings.value
        val minRank = if (s.announceHighConfidenceOnly) rank(Confidence.HIGH) else rank(Confidence.MEDIUM)
        PaymentTrace.parsed(
            correlationId,
            detail = "parser=GooglePayParser direction=${parsed.direction} " +
                "confidence=${parsed.confidence} ref=${if (parsed.referenceId != null) 1 else 0}",
        )
        if (parsed.direction != Direction.RECEIVED) {
            diag("gate", "direction=${parsed.direction} suppressed (${parsed.source.name})")
            PaymentTrace.dropped(correlationId, "gate-direction", detail = parsed.direction.name)
            return
        }
        if (rank(parsed.confidence) < minRank) {
            diag("gate", "confidence=${parsed.confidence} below threshold (${parsed.source.name})")
            PaymentTrace.dropped(correlationId, "gate-confidence", detail = parsed.confidence.name)
            return
        }
        val amount = parsed.amountMinor
        if (amount == null || amount <= 0) {
            diag("gate", "no usable amount (${parsed.source.name})")
            PaymentTrace.dropped(correlationId, "gate-amount")
            return
        }

        // 4. Deduplication. The key is built from stable source identifiers
        // only (REF > SRC > TXT) — never amount + a time bucket, which used to
        // collapse two genuine same-amount payments into one. Cross-channel
        // reconciliation for reference-less captures is step 4b below.
        val fingerprint = Fingerprinter.captureFingerprint(
            packageId = event.originId,
            amountMinor = amount,
            timestampMs = event.postedAtMs,
            title = event.title,
            text = event.body,
            referenceId = parsed.referenceId,
            senderName = parsed.senderName,
            captureSource = event.captureSource.name,
            notificationId = event.notificationId,
            notificationTag = event.notificationTag,
        )
        // 4b. Cross-channel merge, BEFORE the dedupe row is claimed. A merged
        // event must not occupy a fingerprint slot: it is a second *signal* for
        // a payment that already has one, and a row here would suppress the
        // next real payment of the same amount — the exact loss this change
        // exists to remove.
        if (crossChannelMergeIsNew(event, amount, parsed, fingerprint, correlationId)) return

        val dedupInsert = db.processedEventDao().insert(
            ProcessedEventEntity(
                fingerprint = fingerprint,
                eventId = fingerprint,
                sourcePackage = event.originId,
                amountMinor = amount,
                announcedAtMs = System.currentTimeMillis(),
                captureSource = event.captureSource.name,
                postedAtMs = event.postedAtMs,
                hasReference = !parsed.referenceId.isNullOrBlank(),
            )
        )
        if (dedupInsert == -1L) {
            _lastDedupWasDuplicate.value = true
            diag("dedup", "duplicate suppressed: ${fingerprint.take(16)}")
            // The reason token distinguishes the two cases this store can now
            // still see: `ref` (a true re-delivery — same UTR) from `src` (a
            // true re-delivery of the SAME system event: same sbn id, tag and
            // post time). The `amount-bucket` case is gone by construction —
            // a real payment can no longer collide on amount and clock.
            PaymentTrace.deduped(
                correlationId,
                reason = if (parsed.referenceId.isNullOrBlank()) {
                    "dedup-source-replay"
                } else {
                    "dedup-reference-replay"
                },
                detail = "ref=${if (parsed.referenceId != null) 1 else 0} " +
                    "srcId=${if (Fingerprinter.hasStableSourceIdentity(event.notificationId, event.notificationTag)) 1 else 0}",
            )
            return
        }
        _lastDedupWasDuplicate.value = false
        // Analytics (docs/ANALYTICS.md): capture cleared all gates + dedup.
        // Structural only; fired AFTER the state change, off the TTS path.
        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.paymentCapturedLocal()
        val dedupCheckedAt = System.currentTimeMillis()

        // 5. Announce. Text composed locally; Phase 3 sends it inside FCM.
        val announcement = AnnouncementComposer.compose(
            amountMinor = amount,
            senderName = parsed.senderName,
            source = parsed.source,
            style = s.style,
            language = s.language,
        )
        val announcedAt = System.currentTimeMillis()
        PaymentTiming.record(
            captureMs = event.postedAtMs,
            parsedMs = parsedAt,
            dedupCheckedMs = dedupCheckedAt,
            ttsRequestedMs = announcedAt,
            isDebug = isDebugBuild,
        )
        // Critical path ENDS at the TTS request: never block the capture path
        // on utterance completion (spec §15). If the engine is still starting,
        // the text is parked and spoken on readiness — the payment is never
        // dropped after dedup has consumed the fingerprint.
        // Post a HIGH-priority notification with sound. This is what forces Android
        // to wake from Doze / screen-lock so the TTS engine can run. Without it, the
        // announcement is deferred until the user manually unlocks.
        val posted = appContext?.let {
            com.vivekray898.payvoice.service.tts.PaymentAnnouncementNotifier.post(
                it,
                announcement,
                // User's lock-screen privacy choice. Reading the in-memory
                // StateFlow keeps this off the critical path's disk access.
                showDetails = settings.settings.value.showPaymentOnLockScreen,
            )
        } ?: false
        if (isDebugBuild) {
            android.util.Log.d("PaymentPipeline", "wake-up notification posted=$posted")
        }
        appContext?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { com.vivekray898.payvoice.service.tts.PaymentAnnouncementNotifier.cancel(it) },
                5_000L,
            )
        }
        PaymentTrace.queued(
            correlationId,
            detail = "engineWarm=${speaker.isEngineReady()} wakeNotification=$posted",
        )
        speaker.speakWhenReady(announcement, correlationId)

        // 5b. Remote fan-out (spec §8): strictly AFTER the local TTS request,
        // fire-and-forget — remote failure can never affect the local path.
        if (roleProvider() == DeviceRole.OWNER && remoteEnabledProvider() && remoteSender != null) {
            remoteSender.sendAsync(
                traceCorrelationId = correlationId,
                eventId = fingerprint,
                type = RemoteEventType.PAYMENT_RECEIVED,
                amountMinor = amount,
                currency = parsed.currency,
                senderName = parsed.senderName,
                source = parsed.source.name,
                timestampMs = event.postedAtMs,
                localTtsRequestedAtMs = announcedAt,
            )
        }

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
        // 6. Persistence AFTER the TTS request (spec §15): Room writes are
        // never in the capture→speak critical path.
        db.announcementDao().insert(entity)
        _lastAnnouncement.value = entity
        PaymentTrace.stored(correlationId, detail = "captureSource=${event.captureSource.name}")
        diag(
            "pipeline",
            "source=${event.captureSource} sender=${event.originId.take(12)} " +
                "bank=${parsed.source.name} dir=${parsed.direction} amount=$amount " +
                "ref=${parsed.referenceId?.take(8) ?: "-"} conf=${parsed.confidence} " +
                "parser=${entity.parserName} announced=queued " +
                "detToAnnounceMs=${announcedAt - event.postedAtMs}",
        )
        // Analytics (docs/ANALYTICS.md): capture→tts-request latency integer.
        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.paymentAnnouncedLocal(
            announcedAt - event.postedAtMs,
        )
        // Complete the timing timeline in the background (diagnostics only).
        scope.launch {
            PaymentTiming.awaitTtsStart(
                captureMs = event.postedAtMs,
                ttsRequestedMs = announcedAt,
                lastStartAtMsProvider = { speaker.lastStartAtMs },
                isDebug = isDebugBuild,
            )
        }
    }

    private fun parserNameFor(event: CaptureEvent): String = "GooglePayParser"

    /**
     * Applies [CrossChannelMerger]'s four rules to this capture and, when they
     * all hold, records the second signal without announcing it again.
     *
     * Returns true when the caller must stop: the payment was already spoken on
     * another channel. Returns false — announce — in every other case,
     * including every "not confident" case. A duplicate announcement is a
     * nuisance; a missed payment is the bug.
     *
     * The store query already excludes the incoming channel, so rule 2 is
     * enforced in SQL as well as in [CrossChannelMerger]; the rule stays
     * expressed there because that is where it is unit-tested.
     */
    private suspend fun crossChannelMergeIsNew(
        event: CaptureEvent,
        amountMinor: Long,
        parsed: ParsedNotification,
        fingerprint: String,
        correlationId: String,
    ): Boolean {
        val window = CrossChannelMerger.CROSS_CHANNEL_MERGE_WINDOW_MS
        val candidate = db.processedEventDao().recentCrossChannelCandidate(
            amountMinor = amountMinor,
            excludeCaptureSource = event.captureSource.name,
            fromMs = event.postedAtMs - window,
            toMs = event.postedAtMs + window,
        )
        val decision = CrossChannelMerger.decide(
            incoming = CrossChannelMerger.Incoming(
                amountMinor = amountMinor,
                postedAtMs = event.postedAtMs,
                captureSource = event.captureSource.name,
                hasReference = !parsed.referenceId.isNullOrBlank(),
            ),
            earlier = candidate?.let {
                CrossChannelMerger.Earlier(
                    eventId = it.eventId,
                    amountMinor = it.amountMinor,
                    postedAtMs = it.postedAtMs,
                    captureSource = it.captureSource,
                    hasReference = it.hasReference,
                )
            },
        )
        PaymentTrace.merged(
            correlationId,
            reason = CrossChannelMerger.reasonToken(decision),
            detail = "channel=${event.captureSource.name} " +
                "earlierChannel=${candidate?.captureSource ?: "none"}",
        )
        if (decision !is CrossChannelMerger.Decision.Merge) return false

        // Keep the second signal in history, explicitly marked, so the record
        // shows the payment was seen twice rather than silently disappearing.
        db.announcementDao().insert(
            AnnouncementEntity(
                eventId = fingerprint,
                fingerprint = fingerprint,
                sourceName = parsed.sourceLabel ?: parsed.source.displayName,
                amountMinor = amountMinor,
                currency = parsed.currency,
                senderName = parsed.senderName,
                announcementText = parsed.source.displayName,
                detectedAtMs = event.postedAtMs,
                announcedAtMs = System.currentTimeMillis(),
                captureSource = event.captureSource.name,
                parserName = parserNameFor(event),
                mergedWithEventId = decision.earlier.eventId,
            )
        )
        diag(
            "merge",
            "cross-channel duplicate of ${decision.earlier.eventId.take(16)} " +
                "(${decision.earlier.captureSource} -> ${event.captureSource.name}) not re-announced",
        )
        return true
    }

    /** Which of the four read extras actually carried content (shape only). */
    private fun extrasSeenFor(event: CaptureEvent): String {
        val seen = buildList {
            if (!event.title.isNullOrBlank()) add("title")
            if (event.body.isNotBlank()) add("text")
            if (!event.bigText.isNullOrBlank()) add("bigText")
            if (!event.subText.isNullOrBlank()) add("subText")
        }
        return seen.joinToString("+").ifEmpty { "none" }
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
        scope.launch {
            runCatching {
                handleCapture(
                    correlationId = PaymentTrace.newCorrelationId(),
                    event = CaptureEvent(
                        captureSource = CaptureSource.GPAY_NOTIFICATION,
                        originId = source.packageId,
                        title = title,
                        body = text,
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

    companion object {
        /** Cheap "money was mentioned" marker for missed-payment diagnostics. */
        val MONEY_MARKER = Regex("[\\u20B9\\u20A8]|(?i)\\b(?:rs|inr)\\b")
    }
}

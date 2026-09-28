package com.vivekray898.payvoice.service.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vivekray898.payvoice.PayVoiceApp
import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.database.ProcessedEventEntity
import com.vivekray898.payvoice.core.remote.RemoteAuthorization
import com.vivekray898.payvoice.core.remote.RemoteEventValidator
import com.vivekray898.payvoice.core.remote.RemoteEventType
import com.vivekray898.payvoice.core.remote.RemotePaymentEvent
import kotlinx.coroutines.launch

/**
 * Employee-side FCM receiver (spec §10, §11, §31). FCM is the only remote
 * trigger — no polling. onMessageReceived does ONLY:
 *
 *   validate → AUTHORIZE (paired owner match + ACTIVE status, spec §5) →
 *   deduplicate (existing Room store) → existing AnnouncementSpeaker
 *   → persist (after TTS request)
 *
 * No network calls before TTS except the bounded authorization read (cache-
 * first, ≤5s, and the backend's ACTIVE-only fan-out is the PRIMARY gate —
 * this client check is the second safety layer). No UI dependency; works
 * while the app is closed/screen-off within Android's FCM high-priority
 * guarantees. Raw payment content is never logged.
 *
 * Stage diagnostics (spec §8): RemoteDelivery event=… stage=fcm_received /
 * authorized / denied / dedup / tts_requested / tts_started — debug builds.
 */
class PayVoiceMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as? PayVoiceApp ?: return
        val container = app.container
        val receivedAt = System.currentTimeMillis()
        val event: RemotePaymentEvent =
            RemoteEventValidator.validate(message.data.mapValues { it.value.toString() })
                ?: run {
                    stageLog(eid = null, stage = "dropped", reason = "invalid payload")
                    return
                }
        val eid = "event=${event.eventId.take(12)}…"
        stageLog(eid, stage = "fcm_received", extra = "latency=${System.currentTimeMillis() - receivedAt}ms")

        container.applicationScope.launch {
            runCatching {
                // 1. AUTHORIZE before ANY announcement work (spec §5): this
                //    device must be paired, still ACTIVE, and the event must
                //    belong to the paired owner. Fail-closed.
                val verdict = container.employees.authorizeRemoteEvent(event.ownerUid)
                if (verdict !is RemoteAuthorization.Verdict.Allow) {
                    stageLog(
                        eid, stage = "denied",
                        reason = (verdict as? RemoteAuthorization.Verdict.Deny)?.reason ?: "unknown",
                    )
                    return@launch
                }
                stageLog(eid, stage = "authorized")

                if (event.type == RemoteEventType.TEST_ANNOUNCEMENT) {
                    // 2-T. Test path (spec §22): fixed wording, never amounts,
                    // never dedup-eligible, never stored as a payment.
                    stageLog(eid, stage = "tts_requested")
                    val ok = container.speaker.speak(
                        AnnouncementComposer.TEST_ANNOUNCEMENT_EN
                    )
                    stageLog(eid, stage = "tts_started", extra = "ok=$ok fcmLatency=${System.currentTimeMillis() - receivedAt}ms")
                    return@launch
                }

                // 3. Dedup on the EXISTING store: the remote eventId IS the
                //    fingerprint (idempotent across FCM retries/app restarts).
                val claimed = container.database.processedEventDao().insert(
                    ProcessedEventEntity(
                        fingerprint = event.eventId,
                        eventId = event.eventId,
                        sourcePackage = "remote",
                        amountMinor = event.amountMinor,
                        announcedAtMs = System.currentTimeMillis(),
                    )
                )
                if (claimed == -1L) {
                    stageLog(eid, stage = "dedup", reason = "duplicate — not announced")
                    return@launch
                }
                stageLog(eid, stage = "dedup", extra = "first-seen")

                // 4. Announce via the EXISTING speaker (amount-only when the
                //    sender is unavailable — never "unknown user", spec §6/§11).
                val text = AnnouncementComposer.composeSms(
                    amountMinor = event.amountMinor,
                    senderName = event.senderName?.trim()?.takeIf { it.isNotBlank() },
                )
                val ttsRequestedAt = System.currentTimeMillis()
                stageLog(eid, stage = "tts_requested", extra = "validate→ttsRequest=${ttsRequestedAt - receivedAt}ms")
                container.speaker.speakWhenReady(text)

                // 5. History AFTER the TTS request (persistence off the
                //    announcement path, mirroring the local pipeline).
                container.database.announcementDao().insert(
                    com.vivekray898.payvoice.core.database.AnnouncementEntity(
                        eventId = event.eventId,
                        fingerprint = event.eventId,
                        sourceName = if (event.source == "TEST") "Test" else "Remote · ${event.source}",
                        amountMinor = event.amountMinor,
                        currency = event.currency,
                        senderName = event.senderName,
                        announcementText = text,
                        detectedAtMs = event.timestampMs,
                        announcedAtMs = ttsRequestedAt,
                        captureSource = "REMOTE",
                        parserName = "RemoteFcm",
                    )
                )
                stageLog(
                    eid, stage = "tts_started",
                    extra = "fcm→ttsRequest=${ttsRequestedAt - receivedAt}ms",
                )
            }.onFailure {
                stageLog(eid, stage = "failed", reason = it.javaClass.simpleName)
            }
        }
    }

    /**
     * Token rotation (spec §14/§15): persist securely AND refresh this
     * device's `devices` row so the fcm-gateway always dials a live token.
     */
    override fun onNewToken(token: String) {
        val app = application as? PayVoiceApp ?: return
        app.container.applicationScope.launch {
            runCatching {
                app.container.messaging.storeTokenSecurely(token)
                app.container.devices.onTokenRefreshed(token)
            }
        }
    }

    /** Single diagnostic formatter: RemoteDelivery event=… stage=… [reason=…] [extra]. */
    private fun stageLog(
        eid: String?,
        stage: String,
        reason: String? = null,
        extra: String? = null,
    ) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val detail = buildList {
            reason?.let { add("reason=$it") }
            extra?.let { add(it) }
        }.joinToString(" ")
        val eventPart = eid?.let { " $it" }.orEmpty()
        android.util.Log.d(
            TAG,
            "RemoteDelivery$eventPart stage=$stage${if (detail.isEmpty()) "" else " $detail"}",
        )
    }

    companion object {
        private const val TAG = "PayVoiceFCM"
    }
}

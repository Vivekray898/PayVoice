package com.vivekray898.payvoice.service.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vivekray898.payvoice.PayVoiceApp
import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.database.ProcessedEventEntity
import com.vivekray898.payvoice.core.remote.RemoteEventValidator
import com.vivekray898.payvoice.core.remote.RemoteEventType
import com.vivekray898.payvoice.core.remote.RemotePaymentEvent
import kotlinx.coroutines.launch

/**
 * Employee-side FCM receiver (spec §10, §11, §31). FCM is the only remote
 * trigger — no polling. onMessageReceived does ONLY:
 *
 *   validate → deduplicate (existing Room store) → existing AnnouncementSpeaker
 *   → persist (after TTS request)
 *
 * No network calls before TTS; no UI dependency; works while the app is
 * closed/screen-off within Android's FCM high-priority guarantees. Raw
 * payment content is never logged.
 */
class PayVoiceMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as? PayVoiceApp ?: return
        val container = app.container
        val receivedAt = System.currentTimeMillis()

        // 1. Validate (pure, spec §30: bad payloads are rejected silently).
        val event: RemotePaymentEvent =
            RemoteEventValidator.validate(message.data.mapValues { it.value.toString() })
                ?: run {
                    log("dropped: invalid payload")
                    return
                }

        container.applicationScope.launch {
            runCatching {
                if (event.type == RemoteEventType.TEST_ANNOUNCEMENT) {
                    // 2-T. Test path (spec §22): fixed wording, never amounts,
                    // never dedup-eligible, never stored as a payment.
                    val ok = container.speaker.speak(
                        AnnouncementComposer.TEST_ANNOUNCEMENT_EN
                    )
                    log("test announcement spoken=$ok fcmLatency=${System.currentTimeMillis() - receivedAt}ms")
                    return@launch
                }

                // 2. Dedup on the EXISTING store: the remote eventId IS the
                // fingerprint (idempotent across FCM retries/app restarts).
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
                    log("dropped: duplicate event ${event.eventId.take(12)}…")
                    return@launch
                }

                // 3. Announce via the EXISTING speaker (amount-only when the
                // sender is unavailable — never "unknown user", spec §6/§11).
                val text = AnnouncementComposer.composeSms(
                    amountMinor = event.amountMinor,
                    senderName = event.senderName?.trim()?.takeIf { it.isNotBlank() },
                )
                val ttsRequestedAt = System.currentTimeMillis()
                container.speaker.speakWhenReady(text)

                // 4. History AFTER the TTS request (persistence off the
                // announcement path, mirroring the local pipeline).
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
                log(
                    "announced id=${event.eventId.take(12)}… " +
                        "fcm→ttsRequest=${ttsRequestedAt - receivedAt}ms"
                )
            }.onFailure { log("error: ${it.javaClass.simpleName}") }
        }
    }

    /**
     * Token rotation (spec §14): push the fresh token into this device's
     * registry record so the Cloud Function always dials a live token.
     */
    override fun onNewToken(token: String) {
        val app = application as? PayVoiceApp ?: return
        app.container.applicationScope.launch {
            runCatching {
                app.container.messaging.storeTokenSecurely(token)
                app.container.pairing.touchDevice(fcmToken = token)
            }
        }
    }

    private fun log(message: String) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "PayVoiceFCM"
    }
}

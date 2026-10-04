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
import java.lang.reflect.Method

/**
 * Body of the employee's wake-up notification.
 *
 * The owner leg posts the real announcement (PaymentPipeline). The employee leg
 * used to post a fixed "Payment announcement incoming" regardless of the user's
 * lock-screen choice, so an employee who had opted in still saw no amount while
 * the owner's phone showed one — and opting OUT only downgraded visibility
 * while the amount stayed in the notification body. Mirror the owner's wording,
 * and on opt-out drop the details from the text itself rather than relying on
 * VISIBILITY_PRIVATE alone.
 */
internal fun wakeUpNotificationText(announcement: String, showDetails: Boolean): String =
    if (showDetails) announcement else "Payment announcement incoming"

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

        // FCM requires high-priority messages to produce a user-visible notification,
        // or the channel is downgraded to normal priority (delayed delivery in Doze).
        // Post a silent receipt immediately — before any auth/dedup/TTS work.
        PaymentNotification.post(applicationContext)

        // Hold the process for the duration of the work below.
        //
        // Without goAsync() this method returns the moment `launch` is called,
        // Android considers the FCM service finished, and the process becomes
        // killable while the authorization read and the dedup insert are still
        // in flight — the event is then lost with no announcement and no record
        // that it ever arrived. goAsync() keeps the process (and a wakelock)
        // alive until finish() is called, bounded by the platform deadline.
        //
        // finish() must run on EVERY exit path, including coroutine
        // cancellation, which is why it hangs off invokeOnCompletion rather
        // than the end of the try block.
        val finishPendingResult = acquirePendingResult()

        // Set only once the announcement actually happened; every other terminal
        // path must clear the silent receipt posted above (see below).
        var announced = false
        val work = container.applicationScope.launch {
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
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics
                        .remoteDeliveryFailed("denied")
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
                    PaymentNotification.cancel(applicationContext)
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
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics
                        .remoteDeliveryFailed("dedup")
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
                // Wake-up notification (HIGH + sound): forces Android out of Doze /
                // screen-lock so the TTS engine can run. Cancelled shortly after.
                // The wording matches the owner's notification (PaymentPipeline):
                // the real announcement when the user allows details on the lock
                // screen, otherwise generic — so opting out keeps the amount and
                // the sender out of the notification body entirely, not just off
                // the lock screen.
                val showDetails = container.settings.settings.value.showPaymentOnLockScreen
                com.vivekray898.payvoice.service.tts.PaymentAnnouncementNotifier.post(
                    applicationContext,
                    wakeUpNotificationText(text, showDetails),
                    showDetails = showDetails,
                )
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
                    )                    )
                stageLog(
                    eid, stage = "tts_started",
                    extra = "fcm→ttsRequest=${ttsRequestedAt - receivedAt}ms",
                )
                // Analytics (docs/ANALYTICS.md): remote delivery success,
                // latency = FCM receipt → TTS request. Structural only.
                com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics
                    .remoteDeliverySucceeded(ttsRequestedAt - receivedAt)
                // The speaker is async (speakWhenReady returns immediately). Cancel on a
                // short delay — long enough for FCM to register the notification, short
                // enough that the user never sees it linger.
                announced = true
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { PaymentNotification.cancel(applicationContext) },
                    4_000L,
                )
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { com.vivekray898.payvoice.service.tts.PaymentAnnouncementNotifier.cancel(applicationContext) },
                    5_000L,
                )
            }.onFailure {
                stageLog(eid, stage = "failed", reason = it.javaClass.simpleName)
                com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics
                    .remoteDeliveryFailed("failed")
            }
        }

        work.invokeOnCompletion {
            // The silent FCM receipt must never outlive the work that justified
            // it. A DENIED or DUPLICATE event is deliberately NOT announced, and
            // leaving "Payment announced" in the shade says otherwise; the same
            // goes for a thrown or cancelled coroutine. Success keeps it briefly
            // (its own 4 s delay). Doing it here means a new early return cannot
            // forget.
            if (!announced) {
                runCatching { PaymentNotification.cancel(applicationContext) }
            }
            // Reached on success, on failure, and on scope cancellation. If the
            // platform already tore the service down past its own deadline,
            // finish() throws IllegalStateException — swallowing that keeps a
            // late finish from crashing the process we were trying to protect.
            finishPendingResult()
        }
    }

    /**
     * Takes a reference to the platform's `Service.PendingResult` so the
     * process is not killable until [finishPendingResult] is invoked.
     *
     * `goAsync()` is resolved reflectively rather than called directly: it is
     * a real, long-stable platform API, but the SDK platform installed on this
     * machine (`android-37.0`) ships an android.jar whose `android.app.Service`
     * stub omits it, so a direct call does not compile here. Every failure mode
     * (method missing at runtime, invocation throwing, platform already torn
     * the service down) degrades to a no-op instead of crashing — the previous
     * no-goAsync behaviour — so this can only help.
     *
     * @return an idempotent completion callback that must run on every exit
     *   path of the announcement work.
     */
    private fun acquirePendingResult(): () -> Unit {
        val goAsync: Method = runCatching {
            Class.forName("android.app.Service").getMethod("goAsync")
        }.getOrNull() ?: run {
            stageLog(eid = null, stage = "goasync_unavailable")
            return {}
        }
        val pending = runCatching { goAsync.invoke(this) }.getOrNull() ?: return {}
        return {
            runCatching { pending.javaClass.getMethod("finish").invoke(pending) }
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
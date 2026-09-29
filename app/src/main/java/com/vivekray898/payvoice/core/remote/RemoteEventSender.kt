package com.vivekray898.payvoice.core.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owner→Employee event upload (spec §8, §10, §20) — Supabase.
 *
 * Fire-and-forget by contract: [sendAsync] never blocks or fails the local
 * announcement path — the pipeline calls it AFTER the TTS request, on a
 * background scope. The `payment_events.id` IS the eventId (already the
 * pipeline's cross-channel fingerprint), so retries and GPay+SMS double
 * detection collapse into ONE backend row (idempotent upsert).
 *
 * The `fcm-gateway` edge function (invoked by the AFTER INSERT trigger)
 * fans the row out over FCM.
 */
class RemoteEventSender(
    private val auth: PayVoiceAuth,
    private val client: SupabaseClient,
    private val scope: CoroutineScope,
) {

    /** Delivery outcome for the diagnostics screen (spec §32). */
    private val _lastSendState = kotlinx.coroutines.flow.MutableStateFlow<SendState>(SendState.IDLE)
    val lastSendState: kotlinx.coroutines.flow.StateFlow<SendState> = _lastSendState

    sealed class SendState {
        object IDLE : SendState()
        data class SENT(val eventId: String, val atMs: Long) : SendState()
        data class FAILED(val reason: String) : SendState()
    }

    @Volatile
    var lastRemoteSendRequestedAtMs: Long = 0
        private set

    /**
     * Non-blocking send. [localTtsRequestedAtMs] feeds the latency log
     * (spec §24); raw content is never logged.
     */
    fun sendAsync(
        eventId: String,
        type: RemoteEventType,
        amountMinor: Long,
        currency: String,
        senderName: String?,
        source: String,
        timestampMs: Long,
        localTtsRequestedAtMs: Long,
    ) {
        scope.launch {
            val uid = auth.ensureSignedIn() ?: run {
                _lastSendState.value = SendState.FAILED("no-auth")
                return@launch
            }
            val event = RemotePaymentEvent(
                eventId = eventId,
                type = type,
                amountMinor = amountMinor,
                currency = currency,
                senderName = senderName,
                source = source,
                timestampMs = timestampMs,
            )
            lastRemoteSendRequestedAtMs = System.currentTimeMillis()
            val ok = withContext(Dispatchers.IO) {
                client.insertRow(
                    table = RemoteCollections.PAYMENT_EVENTS,
                    body = event.toPayload(uid, localTtsRequestedAtMs),
                    bearer = null,
                    onConflictMerge = true,
                )
            }
            if (ok) {
                _lastSendState.value = SendState.SENT(event.eventId, System.currentTimeMillis())
                com.vivekray898.payvoice.core.util.DebugLog.d(
                    TAG,
                    "remote event accepted id=${event.eventId.take(12)}… " +
                        "capture→accepted=${System.currentTimeMillis() - timestampMs}ms",
                )
            } else {
                _lastSendState.value = SendState.FAILED("send-failed")
                com.vivekray898.payvoice.core.util.DebugLog.w(TAG, "remote send failed (local announcement unaffected)")
            }
        }
    }

    private companion object {
        const val TAG = "RemoteEventSender"
    }
}

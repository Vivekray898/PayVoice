package com.vivekray898.payvoice

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * DEBUG-only latency instrumentation (spec §15): stage timestamps from capture
 * to TTS start, plus the last observed latencies for the diagnostics UI.
 * Never logged in release builds; carries amounts/latencies only — never
 * notification content.
 */
object PaymentTiming {

    data class Stage(
        val captureMs: Long,
        val parsedMs: Long = 0,
        val dedupCheckedMs: Long = 0,
        val ttsRequestedMs: Long = 0,
        val ttsStartedMs: Long = 0,
    ) {
        val parseLatencyMs: Long get() = parsedMs - captureMs
        val dedupLatencyMs: Long get() = dedupCheckedMs - captureMs
        val ttsRequestLatencyMs: Long get() = ttsRequestedMs - captureMs
        val ttsStartLatencyMs: Long get() = if (ttsStartedMs > 0) ttsStartedMs - captureMs else -1
    }

    private val _last = MutableStateFlow<Stage?>(null)

    /** Most recent fully-observed stage timeline (diagnostics UI). */
    val last: StateFlow<Stage?> = _last

    fun record(
        captureMs: Long,
        parsedMs: Long,
        dedupCheckedMs: Long,
        ttsRequestedMs: Long,
        isDebug: Boolean,
    ) {
        val stage = Stage(captureMs, parsedMs, dedupCheckedMs, ttsRequestedMs)
        _last.value = stage
        if (isDebug) {
            android.util.Log.d(
                TAG,
                "capture→ttsRequested=${stage.ttsRequestLatencyMs}ms " +
                    "(parse=${stage.parseLatencyMs}ms dedup=${stage.dedupLatencyMs}ms)",
            )
        }
    }

    /**
     * Completes the timeline when the engine actually starts speaking
     * ([AnnouncementSpeaker.lastStartAtMs] moves past the request). Bounded
     * wait, event-scoped, diagnostics only.
     */
    suspend fun awaitTtsStart(
        captureMs: Long,
        ttsRequestedMs: Long,
        lastStartAtMsProvider: () -> Long,
        isDebug: Boolean,
    ) {
        val started = withTimeoutOrNull(TTS_START_WAIT_MS) {
            while (lastStartAtMsProvider() <= ttsRequestedMs) delay(50)
            lastStartAtMsProvider()
        } ?: return
        val final = (_last.value ?: Stage(captureMs)).copy(ttsStartedMs = started)
        _last.value = final
        if (isDebug) {
            android.util.Log.d(TAG, "capture→ttsStart=${final.ttsStartLatencyMs}ms")
        }
    }

    private const val TTS_START_WAIT_MS = 15_000L
    private const val TAG = "PaymentTiming"
}

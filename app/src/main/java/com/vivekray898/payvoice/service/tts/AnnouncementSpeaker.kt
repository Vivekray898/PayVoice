package com.vivekray898.payvoice.service.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.settings.SettingsRepository
import com.vivekray898.payvoice.core.util.DebugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Local Android TTS wrapper (spec §11, §12).
 *
 *  - Application-scoped singleton (owned by AppContainer): never recreated per
 *    payment, never tied to an Activity/Compose lifecycle.
 *  - Init is lazy but bounded: limited retries with backoff on failure
 *    (engine missing/disconnected) — never an infinite loop.
 *  - Holds a PARTIAL wake lock only for the duration of an utterance (capped).
 *  - Transient may-duck audio focus on the media stream.
 *  - Engine is kept warm briefly after use, then released (no 24/7 service).
 *  - Payment-safe: if a payment arrives before init completes, it is parked in
 *    a single [pendingSlot] (latest wins, per spec §8) and flushed the moment
 *    the engine becomes ready — the payment is never silently lost and the
 *    caller never blocks the notification path.
 */
class AnnouncementSpeaker(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    enum class Status { UNAVAILABLE, INITIALIZING, READY, SPEAKING, ERROR }

    private val _status = MutableStateFlow(Status.UNAVAILABLE)
    val status: StateFlow<Status> = _status

    /** Timestamp (epoch ms) of the most recent TTS start, for latency math. */
    @Volatile
    var lastStartAtMs: Long = 0
        private set

    /** True while a payment is parked waiting for the engine to become ready. */
    val hasPending: Boolean get() = pendingSlot.get() != null

    private val speakMutex = Mutex()
    private val initMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Latest payment announcement waiting for engine readiness (spec: latest wins). */
    private val pendingSlot = AtomicReference<String?>(null)

    private var engine: TextToSpeech? = null
    @Volatile
    private var engineReady = false
    private var initAttempts = 0

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var focusRequest: AudioFocusRequest? = null

    /** Suspends until the utterance completes (or fails/times out). True on success. */
    suspend fun speak(text: String): Boolean = speakMutex.withLock {
        val current = ensureEngine()
        if (current == null) {
            _status.value = Status.ERROR
            return false
        }
        _status.value = Status.SPEAKING
        val utteranceId = "pv_${System.nanoTime()}"
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "payvoice:announce"
        ).apply { acquire(WAKE_LOCK_CAP_MS) }
        try {
            val focusGranted = withContext(Dispatchers.Main) { requestFocus() }
            if (!focusGranted) {
                DebugLog.d(TAG, "audio focus not granted — will still attempt TTS with ducking")
            }
            val s = settings.settings.value
            val done = CompletableDeferred<Boolean>()
            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    lastStartAtMs = System.currentTimeMillis()
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId) done.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) done.complete(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) done.complete(false)
                }
            })
            val startBaseline = lastStartAtMs
            val started = withContext(Dispatchers.Main) {
                current.setSpeechRate(s.speechRate)
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.speechVolume)
                }
                current.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId) >= 0
            }
            // Verify onStart actually fired: if not within 2s, the engine is silent.
            if (started) {
                val startFired = withTimeoutOrNull(2_000L) {
                    while (lastStartAtMs <= startBaseline) delay(50)
                    true
                } ?: false
                if (!startFired) {
                    android.util.Log.e(TAG, "TTS onStart never fired; engine silent — treating as failure")
                    // Fall through to the ok computation, which will time out; the retry
                    // path in Layer 2 will then fire.
                }
            }
            val ok = if (!started) false
            else withTimeoutOrNull(WAKE_LOCK_CAP_MS - 2_000) { done.await() } ?: false
            if (!ok) {
                android.util.Log.w(TAG, "speak() failed; attempting one engine restart + retry")
                runCatching { engine?.shutdown() }
                engine = null
                engineReady = false
                initAttempts = 0
                val restarted = ensureEngine()
                if (restarted == null) {
                    android.util.Log.e(TAG, "TTS engine could not restart after failure; posting fallback")
                    TtsFallbackNotifier.notify(context, text)
                    return false
                }
                val retryId = "pv_retry_${System.nanoTime()}"
                val retryDone = CompletableDeferred<Boolean>()
                restarted.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { lastStartAtMs = System.currentTimeMillis() }
                    override fun onDone(id: String?) { if (id == retryId) retryDone.complete(true) }
                    @Deprecated("deprecated") override fun onError(id: String?) {
                        if (id == retryId) retryDone.complete(false)
                    }
                    override fun onError(id: String?, code: Int) {
                        if (id == retryId) retryDone.complete(false)
                    }
                })
                val retryStarted = withContext(Dispatchers.Main) {
                    restarted.speak(text, TextToSpeech.QUEUE_FLUSH, null, retryId) >= 0
                }
                val retryOk = if (!retryStarted) false
                    else withTimeoutOrNull(WAKE_LOCK_CAP_MS - 2_000) { retryDone.await() } ?: false
                if (!retryOk) {
                    android.util.Log.e(TAG, "TTS retry also failed; posting fallback")
                    TtsFallbackNotifier.notify(context, text)
                } else {
                    DebugLog.d(TAG, "TTS retry succeeded")
                }
                return retryOk
            }
            return ok
        } finally {
            withContext(Dispatchers.Main) { abandonFocus() }
            runCatching { if (wakeLock.isHeld) wakeLock.release() }
            _status.value = Status.READY
            scheduleIdleRelease()
        }
    }

    /**
     * Fire-and-forget announcement for the notification/SMS critical path:
     * if the engine is ready this speaks immediately (via [speak]); otherwise
     * it parks the text in [pendingSlot] and kicks off init — the parked text
     * is spoken when readiness lands. Returns immediately; never blocks the
     * capture thread.
     */
    fun speakWhenReady(text: String) {
        if (engineReady) {
            containerLaunch { speak(text) }
            return
        }
        // Latest-wins parking (unchanged behavior).
        pendingSlot.set(text)
        if (engineReady) {
            if (flushPending()) return
        }
        containerLaunch {
            val engine = ensureEngine()
            if (engine == null) {
                // Init failed entirely — never lose the announcement.
                val parked = pendingSlot.getAndSet(null)
                if (parked != null) {
                    android.util.Log.e(
                        TAG,
                        "TTS engine unavailable after $initAttempts attempts; posting fallback notification"
                    )
                    TtsFallbackNotifier.notify(context, parked)
                }
            } else {
                flushPending()
            }
        }
    }

    private fun containerLaunch(block: suspend () -> Unit) {
        val app = context.applicationContext as? com.vivekray898.payvoice.PayVoiceApp
        if (app != null) {
            app.container.applicationScope.launch { runCatching { block() } }
        } else {
            // Fallback scope (should not happen in this app).
            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch { runCatching { block() } }
        }
    }

    private fun flushPending(): Boolean {
        val text = pendingSlot.getAndSet(null) ?: return false
        containerLaunch { speak(text) }
        return true
    }

    /** Pre-initializes the engine (app start / setup screens) so first real use is instant. */
    suspend fun warmUp() {
        ensureEngine()
        scheduleIdleRelease()
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(this) {
            engine?.let { runCatching { it.shutdown() } }
            engine = null
            engineReady = false
        }
        _status.value = Status.UNAVAILABLE
    }

    private suspend fun ensureEngine(): TextToSpeech? = initMutex.withLock {
        engine?.takeIf { engineReady }?.let { return it }
        // Bounded retry: engine missing/disconnected is often transient
        // (Google TTS updating, user switching engines) but never retried
        // forever (spec §8).
        if (initAttempts >= MAX_INIT_ATTEMPTS) {
            _status.value = Status.ERROR
            return null
        }
        initAttempts++
        _status.value = Status.INITIALIZING
        val initStatus = CompletableDeferred<Int>()
        val candidate = TextToSpeech(context) { status -> initStatus.complete(status) }
        val status = runCatching {
            withTimeoutOrNull(INIT_TIMEOUT_MS) { initStatus.await() }
        }.getOrNull() ?: TextToSpeech.ERROR
        if (status != TextToSpeech.SUCCESS) {
            runCatching { candidate.shutdown() }
            if (initAttempts >= MAX_INIT_ATTEMPTS) _status.value = Status.ERROR
            else {
                _status.value = Status.UNAVAILABLE
                mainHandler.postDelayed(
                    { containerLaunch { ensureEngine() } },
                    INIT_RETRY_BACKOFF_MS,
                )
            }
            return null
        }
        val locale = Locale.forLanguageTag(currentLocaleTag())
        val langResult = candidate.setLanguage(locale)
        if (langResult < TextToSpeech.LANG_AVAILABLE) {
            // Missing voice for requested language: fall back to en-IN, then US.
            candidate.setLanguage(Locale("en", "IN"))
        }
        engine = candidate
        engineReady = true
        initAttempts = 0
        _status.value = Status.READY
        // A payment may have arrived while init was in flight — speak it now.
        flushPending()
        candidate
    }

    private fun currentLocaleTag(): String =
        settings.settings.value.language.ttsLocaleTag

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            android.util.Log.w(TAG, "audio focus denied: $result")
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun scheduleIdleRelease() {
        mainHandler.removeCallbacks(idleReleaseRunnable)
        mainHandler.postDelayed(idleReleaseRunnable, IDLE_RELEASE_MS)
    }

    private val idleReleaseRunnable = Runnable { release() }

    companion object {
        private const val TAG = "PayVoiceTTS"
        private const val WAKE_LOCK_CAP_MS = 45_000L
        private const val IDLE_RELEASE_MS = 5 * 60_000L
        private const val MAX_INIT_ATTEMPTS = 3
        private const val INIT_RETRY_BACKOFF_MS = 4_000L
        private const val INIT_TIMEOUT_MS = 10_000L
    }
}

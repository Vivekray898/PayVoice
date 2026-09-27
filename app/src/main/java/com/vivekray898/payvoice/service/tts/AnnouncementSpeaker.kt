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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Local Android TTS wrapper (spec §11, §12).
 *
 *  - No network, no cloud voice: works with zero connectivity.
 *  - Init is lazy — only on the announcement path or an explicit warmUp().
 *  - Holds a PARTIAL wake lock only for the duration of an utterance (capped).
 *  - Transient may-duck audio focus on the media stream.
 *  - Engine is kept warm briefly after use, then released (no 24/7 service).
 */
class AnnouncementSpeaker(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    enum class Status { UNAVAILABLE, INITIALIZING, READY, SPEAKING, ERROR }

    private val _status = MutableStateFlow(Status.UNAVAILABLE)
    val status: StateFlow<Status> = _status

    private val speakMutex = Mutex()
    private val initMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var engine: TextToSpeech? = null
    private var engineReady = false

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
            withContext(Dispatchers.Main) { requestFocus() }
            val s = settings.settings.value
            val done = CompletableDeferred<Boolean>()
            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
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
            val started = withContext(Dispatchers.Main) {
                current.setSpeechRate(s.speechRate)
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.speechVolume)
                }
                current.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId) >= 0
            }
            val ok = if (!started) false
            else withTimeoutOrNull(WAKE_LOCK_CAP_MS - 2_000) { done.await() } ?: false
            return ok
        } finally {
            withContext(Dispatchers.Main) { abandonFocus() }
            runCatching { if (wakeLock.isHeld) wakeLock.release() }
            _status.value = Status.READY
            scheduleIdleRelease()
        }
    }

    /** Pre-initializes the engine (settings/test screens) so first real use is instant. */
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
        _status.value = Status.INITIALIZING
        val initStatus = CompletableDeferred<Int>()
        val candidate = TextToSpeech(context) { status -> initStatus.complete(status) }
        val status = runCatching { initStatus.await() }.getOrDefault(TextToSpeech.ERROR)
        if (status != TextToSpeech.SUCCESS) {
            runCatching { candidate.shutdown() }
            _status.value = Status.ERROR
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
        _status.value = Status.READY
        candidate
    }

    private fun currentLocaleTag(): String =
        settings.settings.value.language.ttsLocaleTag

    private fun requestFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
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
        private const val WAKE_LOCK_CAP_MS = 45_000L
        private const val IDLE_RELEASE_MS = 5 * 60_000L
    }
}

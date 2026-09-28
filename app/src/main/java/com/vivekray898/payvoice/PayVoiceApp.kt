package com.vivekray898.payvoice

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.vivekray898.payvoice.service.messaging.PaymentNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Minimal application class.
 *
 * Phase-1 constraint: no expensive initialization on the main thread. The
 * notification path (and the FCM path in a later phase) must never pay for
 * UI-stack startup cost. Everything is lazy: the container is only built when
 * the UI or the listener pipeline actually touches it.
 *
 * Post-Clear-Data reliability: if Notification Access is still granted but the
 * platform has not re-bound the listener, request exactly one supported
 * rebind ([ListenerRuntimeState.scheduleStartupRepair]) after a short grace
 * period. TTS warms up asynchronously so the first payment speaks instantly.
 */
class PayVoiceApp : Application(), Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()

        // Create the silent "payment announced" notification channel up front.
        // It exists so the FCM high-priority channel is not downgraded to
        // normal priority by Google (which delays delivery in Doze). Cheap
        // and idempotent; safe to call on every cold start.
        PaymentNotification.ensureChannel(this)

        // Off-main: schedule the daily retention pass (WorkManager is on-demand
        // initialized, so this must run after onCreate starts). No other startup
        // work happens here — the notification path never pays UI-startup cost.
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                com.vivekray898.payvoice.core.database.RetentionWorker.schedule(this@PayVoiceApp)
            }
            // One-shot stuck-listener repair (grant present, binding absent).
            runCatching {
                container.listenerRuntime.scheduleStartupRepair(this@PayVoiceApp)
            }
            // Anonymous auth warm-up (device identity for the remote layer).
            // Fire-and-forget: remote features degrade gracefully offline.
            runCatching { container.auth.warmUp() }
            // Device heartbeat: keep last_seen fresh for the owner card.
            runCatching { container.devices.touch() }
            // Async TTS engine warm-up — never blocks startup, never blocks TTS.
            runCatching {
                container.speaker.warmUp()
            }.onFailure {
                if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    Log.d("PayVoiceApp", "TTS warm-up failed: ${it.javaClass.simpleName}")
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
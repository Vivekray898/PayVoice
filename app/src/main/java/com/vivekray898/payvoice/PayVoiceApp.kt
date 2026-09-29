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
            // Doze visibility: log (debug builds only) when battery optimization
            // is still active — the #1 silent cause of delayed listener
            // callbacks and FCM delivery on stock Android.
            if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                val pm = getSystemService(android.os.PowerManager::class.java)
                val exempt = pm?.isIgnoringBatteryOptimizations(packageName) == true
                if (!exempt) {
                    Log.d("PayVoiceApp", "battery optimization ACTIVE (exemption missing) — background delivery may be delayed")
                }
            }
            // Device heartbeat: keep last_seen fresh for the owner card.
            runCatching { container.devices.touch() }
            // Token freshness (reliability fix): a stale FCM token means the
            // gateway dials a dead registration and employees silently stop
            // hearing payments. This runs at PROCESS start — the FCM-delivery
            // cold-start path never opens an Activity, so MainViewModel's
            // UI-open registration does not cover it. Bounded, fire-and-forget.
            if (container.messaging.ensureFirebaseInitialized()) {
                runCatching { refreshStaleFcmToken() }.onFailure {
                    if (isDebugBuild()) {
                        Log.d("PayVoiceApp", "token freshness check skipped: ${it.javaClass.simpleName}")
                    }
                }
            }
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

    private fun isDebugBuild(): Boolean =
        (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * If this device's registry row was token-refreshed more than
     * [STALE_TOKEN_MS] ago (or has no row yet), force an FCM token fetch and
     * re-register. Network-less starts fail softly (getOrNull → return).
     */
    private suspend fun refreshStaleFcmToken() {
        val lastRefresh = runCatching {
            container.devices.ownDevice()?.tokenRefreshedAtMs ?: 0L
        }.getOrDefault(0L)
        val ageMs = System.currentTimeMillis() - lastRefresh
        if (lastRefresh > 0L && ageMs <= STALE_TOKEN_MS) return
        if (isDebugBuild()) {
            Log.d(
                "PayVoiceApp",
                if (lastRefresh == 0L) "token freshness unknown (no devices row) — refreshing"
                else "FCM token stale (${ageMs / 3_600_000L}h) — refreshing",
            )
        }
        val token = runCatching { container.messaging.refreshToken().getOrNull() }
            .getOrNull() ?: return
        container.devices.registerDevice(
            fcmToken = token,
            deviceName = container.settings.settings.value.deviceName
                .ifBlank { android.os.Build.MODEL ?: "Device" },
        )
    }

    private companion object {
        /** Re-register when the stored token is older than 7 days. */
        const val STALE_TOKEN_MS = 7 * 24 * 3_600_000L
    }
}
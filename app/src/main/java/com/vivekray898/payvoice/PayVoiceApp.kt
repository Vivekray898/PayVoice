package com.vivekray898.payvoice

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Minimal application class.
 *
 * Phase-1 constraint: no expensive initialization here. The notification path
 * (and the FCM path in a later phase) must never pay for UI-stack startup cost.
 * Everything is lazy: the container is only built when the UI or the listener
 * pipeline actually touches it.
 */
class PayVoiceApp : Application(), Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Off-main: schedule the daily retention pass (WorkManager is on-demand
        // initialized, so this must run after onCreate starts). No other startup
        // work happens here — the notification path never pays UI-startup cost.
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                com.vivekray898.payvoice.core.database.RetentionWorker.schedule(this@PayVoiceApp)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

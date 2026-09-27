package com.vivekray898.payvoice

import android.app.Application
import androidx.work.Configuration

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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

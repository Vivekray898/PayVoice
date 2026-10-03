package com.vivekray898.payvoice.core.health

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.vivekray898.payvoice.core.model.KnownPackages
import com.vivekray898.payvoice.service.setup.SetupNotifications

/**
 * Real device state behind [HealthSources].
 *
 * FCM registration and the Supabase session are passed in as lambdas rather
 * than probed here: those live in [com.vivekray898.payvoice.service.messaging
 * .MessagingRepository] and [com.vivekray898.payvoice.core.remote.PayVoiceAuth],
 * and re-querying Firebase/OkHttp from a status line would be both slower and
 * a second source of truth.
 *
 * Every method is a cheap read; the checker runs them off the main thread.
 */
class AndroidHealthSources(
    private val context: Context,
    private val fcmTokenRegisteredSource: () -> Boolean,
    private val sessionValid: () -> Boolean,
) : HealthSources {

    override fun notificationsEnabled(): Boolean =
        SetupNotifications.canPostNotifications(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun runtimeNotificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    override fun paymentChannelImportance(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return ChannelImportance.DEFAULT
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(SetupNotifications.CHANNEL_PAYMENT_EVENTS)
            ?: return ChannelImportance.NONE
        // Android's IMPORTANCE_* values map 1:1 onto ChannelImportance.
        return channel.importance
    }

    override fun doNotDisturbActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // ANY filter other than ALL suppresses or reshapes an announcement.
        return manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    override fun batteryExempt(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    override fun listenerAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    override fun ttsEngineInstalled(): Boolean = runCatching {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        context.packageManager.resolveService(intent, 0) != null
    }.getOrDefault(false)

    override fun ttsVoiceAvailable(languageTag: String): Boolean =
        // Voice data belongs to the TTS engine. Proving a specific locale's
        // data is installed means binding the engine, which is far too heavy
        // for a status line, so the engine's own settings screen is the fix
        // target and "engine present" is what we can honestly assert cheaply.
        ttsEngineInstalled()

    override fun notificationVolume(): Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getStreamVolume(AudioManager.STREAM_NOTIFICATION)

    override fun maxNotificationVolume(): Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

    override fun online(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // The backing lambda is NOT called `fcmTokenRegistered`: a constructor
    // property and a member method with the same name made the unqualified
    // call resolve to the METHOD, so this was unbounded recursion. The
    // StackOverflowError was swallowed by runCatching and reported as
    // "not registered" forever, which is why Health said this device was
    // never registered no matter how many times the token registered.
    override fun fcmTokenRegistered(): Boolean =
        runCatching { fcmTokenRegisteredSource() }.getOrDefault(false)

    override fun supabaseSessionValid(): Boolean = runCatching { sessionValid() }.getOrDefault(false)

    override fun playServicesAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    override fun paymentAppInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(KnownPackages.GOOGLE_PAY, 0)
        true
    }.getOrDefault(false)

    override fun oemNeedsAutostartHint(): Boolean {
        val m = Build.MANUFACTURER?.lowercase() ?: return false
        return m in OEM_AUTOSTART_MANUFACTURERS
    }

    companion object {
        /**
         * Manufacturers whose background policies kill apps without an
         * explicit autostart grant. Kept in one place so the list is trivial
         * to extend — these pages are best-effort and never guaranteed.
         */
        val OEM_AUTOSTART_MANUFACTURERS = setOf(
            "xiaomi", "redmi", "poco",
            "oppo", "realme",
            "vivo",
            "oneplus",
            "huawei", "honor",
        )
    }
}

package com.vivekray898.payvoice.service.status

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vivekray898.payvoice.core.model.KnownPackages
import com.vivekray898.payvoice.service.setup.SetupNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase-1 device status checks. All queries are coroutine-safe: [snapshot]
 * is suspend and intended to run off the main thread (the 91-skipped-frames
 * jank came from running these synchronously during composition).
 *
 * The three permissions are deliberately separate (spec):
 *  A. Notification Listener access — READ GPay payment notifications (the
 *     only notification source; bank payments arrive via SMS)
 *  B. POST_NOTIFICATIONS          — PayVoice's own notifications
 *  C. Battery optimization        — background reliability
 */
class DeviceStatusMonitor(private val context: Context) {

    data class InstalledApp(val displayName: String, val packageName: String, val installed: Boolean, val versionName: String?)

    data class Snapshot(
        val listenerEnabled: Boolean,
        val notificationsEnabled: Boolean,
        val batteryExempt: Boolean,
        val smsPermissionGranted: Boolean,
        val gpay: InstalledApp,
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val romHint: String,
        val isXiaomiFamily: Boolean,
    ) {
        val setupStepsDone: Int
            get() = listOf(listenerEnabled, notificationsEnabled, batteryExempt).count { it }
    }

    fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** App-package query — allowed by the manifest <queries> entries. */
    private fun inspect(packageName: String, displayName: String): InstalledApp {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(packageName, 0)
            InstalledApp(displayName, packageName, true, pkgInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            InstalledApp(displayName, packageName, false, null)
        }
    }

    suspend fun snapshot(): Snapshot = withContext(Dispatchers.Default) {
        val info = DeviceSettingsHelper.detect(context)
        val gpay = inspect(KnownPackages.GOOGLE_PAY, "Google Pay")
        Snapshot(
            listenerEnabled = isListenerEnabled(),
            notificationsEnabled = SetupNotifications.canPostNotifications(context),
            batteryExempt = isIgnoringBatteryOptimizations(),
            smsPermissionGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED,
            gpay = gpay,
            manufacturer = info.manufacturer,
            model = info.model,
            androidVersion = info.androidVersion,
            romHint = info.romHint,
            isXiaomiFamily = info.isXiaomiFamily,
        )
    }

    // ---- Intents (spec: A uses listener settings; B runtime permission; C tiered) ----

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appNotificationSettingsIntent(): Intent = Intent().apply {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    fun launchBatteryFix(): String = DeviceSettingsHelper.launchBatteryFix(context)

    fun appDetailsIntent(): Intent = DeviceSettingsHelper.appDetailsIntent(context)
}

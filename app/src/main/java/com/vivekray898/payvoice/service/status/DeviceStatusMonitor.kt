package com.vivekray898.payvoice.service.status

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Phase-1 device status checks (spec §32 subset; the full HyperOS reliability
 * screen is Phase 4). Pure queries + intent builders — no settings hacking.
 */
class DeviceStatusMonitor(private val context: Context) {

    data class Snapshot(
        val listenerEnabled: Boolean,
        val notificationsEnabled: Boolean,
        val batteryExempt: Boolean,
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val isXiaomi: Boolean,
    )

    fun snapshot(): Snapshot {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty()
        return Snapshot(
            listenerEnabled = isListenerEnabled(),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryExempt = pm.isIgnoringBatteryOptimizations(context.packageName),
            manufacturer = manufacturer,
            model = android.os.Build.MODEL.orEmpty(),
            androidVersion = android.os.Build.VERSION.RELEASE.orEmpty(),
            isXiaomi = manufacturer.contains("xiaomi", ignoreCase = true) ||
                manufacturer.contains("redmi", ignoreCase = true),
        )
    }

    /** Whether this app holds Notification Listener access right now. */
    fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appNotificationSettingsIntent(): Intent = Intent().apply {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    /** Deep link into this app's battery-optimization exemption dialog. */
    fun batteryOptimizationIntent(): Intent = Intent().apply {
        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = Uri.parse("package:${context.packageName}")
    }

    fun generalBatteryOptimizationListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * HyperOS/MIUI autostart screen, best-effort. Returns null when the
     * component is absent (non-Xiaomi or changed ROM) — callers fall back to
     * instructions instead of crashing (spec §37: no hacks).
     */
    fun xiaomiAutostartIntent(): Intent? = runCatching {
        Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.getOrNull()
}

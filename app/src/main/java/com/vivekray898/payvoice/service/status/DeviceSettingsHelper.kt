package com.vivekray898.payvoice.service.status

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * ROM/manufacturer-aware settings navigation (spec: D). Detects the device
 * family and provides SAFE, tiered fallbacks for every settings screen. No
 * hidden APIs, no root, no AccessibilityService — if a manufacturer-specific
 * screen is unavailable we fall back to standard Android settings, and the
 * button never does nothing.
 */
object DeviceSettingsHelper {

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val sdkInt: Int,
        val buildDisplay: String,
        val isXiaomiFamily: Boolean,
        val romHint: String,
    )

    fun detect(context: Context): DeviceInfo {
        val manufacturer = (Build.MANUFACTURER ?: "").trim()
        val isMi = manufacturer.equals("xiaomi", true) ||
            manufacturer.equals("redmi", true) ||
            manufacturer.equals("poco", true)
        val display = (Build.DISPLAY ?: "").trim()
        return DeviceInfo(
            manufacturer = manufacturer.replaceFirstChar { it.uppercase() },
            model = Build.MODEL ?: "",
            androidVersion = Build.VERSION.RELEASE ?: "",
            sdkInt = Build.VERSION.SDK_INT,
            buildDisplay = display,
            isXiaomiFamily = isMi,
            // Public Build.DISPLAY carries the ROM identity on custom ROMs
            // (e.g. crDroid includes its name/version). No hidden APIs.
            romHint = detectRomHint(display),
        )
    }

    private fun detectRomHint(buildDisplay: String): String {
        val markers = listOf("crDroid", "crdroid", "Lineage", "lineage", "PixelOS", "evolution")
        return markers.firstOrNull { buildDisplay.contains(it, ignoreCase = false) } ?: ""
    }

    // ---- Battery optimization: tiered fallbacks (spec: C) ----
    //
    // Tier order in [launchBatteryFix] is Play-policy-driven: the list screen
    // is the default, the direct exemption dialog is the last resort.

    /** Direct exemption dialog — needs the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission. */
    fun directBatteryExemptionIntent(context: Context): Intent = Intent().apply {
        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = Uri.parse("package:${context.packageName}")
    }

    /** Tier 2: the full list of apps with battery optimization toggles. */
    fun batteryOptimizationListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Tier 3: general battery settings. */
    fun batterySettingsIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        action = Settings.ACTION_BATTERY_SAVER_SETTINGS
    }

    /** Tier 4 (last resort): PayVoice's own app details page. Never dead. */
    fun appDetailsIntent(context: Context): Intent = Intent().apply {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Launches the best available battery screen, walking the fallback chain
     * until one resolves. Returns which tier actually launched (for the UI).
     *
     * Order is deliberate and Play-policy-driven: the **list** screen comes
     * first because `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs a
     * declared permission form on Google Play before it may be the default
     * path. The direct exemption dialog is the last resort, reached only on
     * devices where none of the standard screens exist.
     */
    fun launchBatteryFix(context: Context): String {
        val attempts = listOf(
            "optimization-list" to batteryOptimizationListIntent(),
            "battery-settings" to batterySettingsIntent(),
            "exemption-dialog" to directBatteryExemptionIntent(context),
            "app-details" to appDetailsIntent(context),
        )
        for ((tier, intent) in attempts) {
            if (runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrDefault(false)
            ) {
                return tier
            }
        }
        return "none"
    }

    /**
     * Xiaomi Security app battery screen — best effort only. Returns null when
     * the component does not exist (non-Xiaomi or ROM change). Used as an
     * OPTIONAL extra entry point, never as a dependency.
     */
    fun xiaomiBatterySaverIntent(context: Context): Intent? = runCatching {
        val pm = context.packageManager
        val candidates = listOf(
            ComponentNameSpec(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            ),
            ComponentNameSpec(
                "com.miui.securitycenter",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            ),
        )
        for (spec in candidates) {
            val intent = Intent().apply {
                component = android.content.ComponentName(spec.pkg, spec.cls)
                putExtra("package_name", context.packageName)
                putExtra("package_label", "PayVoice")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (pm.resolveActivity(intent, 0) != null) return intent
        }
        null
    }.getOrNull()

    private data class ComponentNameSpec(val pkg: String, val cls: String)
}

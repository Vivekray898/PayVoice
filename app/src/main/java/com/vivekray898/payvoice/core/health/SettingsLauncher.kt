package com.vivekray898.payvoice.core.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import com.vivekray898.payvoice.service.setup.SetupNotifications

/**
 * Turns a [SettingsAction] into an ordered chain of intents and launches the
 * first one that actually resolves.
 *
 * The contract that makes the fix button trustworthy: **it never does
 * nothing**. Every chain terminates in the app's own details page, which
 * always exists, and every step is wrapped so a ROM that has dropped a
 * standard action degrades to the next one instead of crashing with
 * `ActivityNotFoundException`.
 *
 * Play policy note: [SettingsAction.BATTERY_OPTIMIZATION] opens the *list*
 * screen first. The direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
 * dialog is only reached through [SettingsAction.BATTERY_OPTIMIZATION_DIRECT],
 * which the UI only offers as an explicit second action — Google Play wants a
 * declared permission form before that dialog may be the default path.
 */
class SettingsLauncher(private val context: Context) {

    /** Ordered candidates for [action]. Never empty. */
    fun intents(action: SettingsAction): List<Intent> = when (action) {
        SettingsAction.POST_NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    // The system dialog — only offered while the user has not
                    // permanently denied. The UI decides; if the OS refuses
                    // the launcher simply never starts and we fall through.
                    appNotificationSettings(),
                    appDetails(),
                )
            } else {
                listOf(appNotificationSettings(), appDetails())
            }

        SettingsAction.APP_NOTIFICATIONS ->
            listOf(appNotificationSettings(), appDetails())

        SettingsAction.CHANNEL_NOTIFICATIONS ->
            listOf(channelSettings(), appNotificationSettings(), appDetails())

        SettingsAction.NOTIFICATION_LISTENER ->
            listOf(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                appDetails(),
            )

        SettingsAction.BATTERY_OPTIMIZATION ->
            listOf(
                // Play-safe order: list screen, then general battery, then us.
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                appDetails(),
            )

        SettingsAction.BATTERY_OPTIMIZATION_DIRECT ->
            listOf(
                directBatteryExemption(),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                appDetails(),
            )

        SettingsAction.DND_ACCESS ->
            listOf(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                appDetails(),
            )

        SettingsAction.TTS_OUTPUT ->
            listOf(
                // android.provider.Settings has no public constant for this
                // action on every SDK this app supports, so the literal
                // platform action string is used — it is part of the stable
                // android.settings contract, not an implementation detail.
                Intent(ACTION_TEXT_TO_SPEECH_SETTINGS),
                Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA),
                appDetails(),
            )

        SettingsAction.TTS_INSTALL_DATA ->
            listOf(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                Intent(ACTION_TEXT_TO_SPEECH_SETTINGS),
                appDetails(),
            )

        SettingsAction.VOLUME ->
            listOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                } else {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                },
                appDetails(),
            )

        SettingsAction.WIRELESS ->
            listOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                } else {
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                },
                appDetails(),
            )

        SettingsAction.APP_DETAILS -> listOf(appDetails())

        // The user needs to INSTALL the package, not inspect PayVoice's own
        // app-info screen. web fallback covers devices with no Play Store.
        SettingsAction.PLAY_STORE_APP -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_STORE_PACKAGE"))
                .setPackage("com.android.vending"),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"),
            ),
        )

        SettingsAction.OEM_AUTOSTART ->
            oemAutostartIntents() + appDetails()

        SettingsAction.NONE -> emptyList()
    }

    /**
     * Launch the best candidate. Returns the index that fired (0-based) or -1
     * when nothing could be started — callers may treat -1 as "no fix here".
     */
    fun launch(action: SettingsAction): Int {
        val candidates = intents(action)
        candidates.forEachIndexed { index, intent ->
            if (launchSafely(intent)) return index
        }
        return -1
    }

    /** Never throws: a broken ROM must not take the health screen down. */
    private fun launchSafely(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun appDetails(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    private fun appNotificationSettings(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun channelSettings(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, SetupNotifications.CHANNEL_PAYMENT_EVENTS)
        } else {
            appNotificationSettings()
        }

    private fun directBatteryExemption(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * OEM autostart screens, best effort, behind a `resolveActivity` check.
     * Not guaranteed — ROM updates rename these components constantly — which
     * is why they live in one function and always fall through to app details.
     */
    private fun oemAutostartIntents(): List<Intent> {
        val pm = context.packageManager
        return OEM_AUTOSTART_COMPONENTS.mapNotNull { (pkg, cls) ->
            runCatching {
                val intent = Intent().apply {
                    component = android.content.ComponentName(pkg, cls)
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", context.packageName)
                }
                if (pm.resolveActivity(intent, 0) != null) intent else null
            }.getOrNull()
        }
    }

    companion object {
        /**
         * The one package Health can tell the user to install: the payment
         * app whose notifications we are here to listen for.
         */
        private const val PLAY_STORE_PACKAGE =
            com.vivekray898.payvoice.core.model.KnownPackages.GOOGLE_PAY

        /** `android.settings.TEXT_TO_SPEECH_SETTINGS` — stable platform action. */
        private const val ACTION_TEXT_TO_SPEECH_SETTINGS =
            "android.settings.TEXT_TO_SPEECH_SETTINGS"

        /** One file, one list — see [oemAutostartIntents]. */
        private val OEM_AUTOSTART_COMPONENTS = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oplus.securitypermission" to "com.oplus.securitypermission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        )
    }
}

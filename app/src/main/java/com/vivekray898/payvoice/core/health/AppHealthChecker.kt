package com.vivekray898.payvoice.core.health

import com.vivekray898.payvoice.R

/**
 * Every piece of device state the health checker reads, as plain JVM-safe
 * types. `AndroidHealthSources` is the real implementation; tests supply a
 * fake and never touch the Android SDK.
 */
interface HealthSources {
    fun notificationsEnabled(): Boolean
    /**
     * POST_NOTIFICATIONS as a runtime permission (API 33+), or null below
     * where it is install-time and there is no dialog to show.
     */
    fun runtimeNotificationPermission(): String?
    fun paymentChannelImportance(): Int
    fun doNotDisturbActive(): Boolean
    fun batteryExempt(): Boolean
    fun listenerAccessGranted(): Boolean
    fun ttsEngineInstalled(): Boolean
    fun ttsVoiceAvailable(languageTag: String): Boolean
    fun notificationVolume(): Int
    fun maxNotificationVolume(): Int
    fun online(): Boolean
    fun fcmTokenRegistered(): Boolean
    fun supabaseSessionValid(): Boolean
    fun playServicesAvailable(): Boolean
    fun paymentAppInstalled(): Boolean
    fun oemNeedsAutostartHint(): Boolean
}

/**
 * The single source of truth for "is this phone ready to announce payments".
 *
 * Injected so Home and Settings show the *same* list, and re-evaluated on
 * every ON_RESUME so a fix made in system Settings appears immediately without
 * restarting the app.
 *
 * Deliberately free of Android APIs: everything arrives through
 * [HealthSources], so the whole decision table is a JVM unit test.
 */
interface AppHealthChecker {
    suspend fun check(request: HealthRequest): List<HealthItem>
}

class DefaultAppHealthChecker(
    private val sources: HealthSources,
) : AppHealthChecker {

    override suspend fun check(request: HealthRequest): List<HealthItem> {
        val items = mutableListOf<HealthItem>()

        // ---- Notifications -------------------------------------------------
        val notificationsOk = sources.notificationsEnabled()
        items += HealthItem(
            id = HealthIds.NOTIFICATIONS,
            title = R.string.health_notifications_title,
            why = R.string.health_notifications_why,
            level = if (notificationsOk) HealthLevel.OK else HealthLevel.BLOCKED,
            fixLabel = if (notificationsOk) null else R.string.health_fix_turn_on,
            settingsAction = if (notificationsOk) SettingsAction.NONE else SettingsAction.POST_NOTIFICATIONS,
            runtimePermission = if (notificationsOk) null else sources.runtimeNotificationPermission(),
        )

        // ---- Channel importance (meaningless once notifications are off) ---
        if (notificationsOk) {
            val importance = sources.paymentChannelImportance()
            val channelOk = importance >= ChannelImportance.DEFAULT
            items += HealthItem(
                id = HealthIds.CHANNEL,
                title = R.string.health_channel_title,
                why = R.string.health_channel_why,
                level = if (channelOk) HealthLevel.OK else HealthLevel.WARN,
                fixLabel = if (channelOk) null else R.string.health_fix_open_settings,
                settingsAction = if (channelOk) {
                    SettingsAction.NONE
                } else {
                    SettingsAction.CHANNEL_NOTIFICATIONS
                },
            )
        }

        // ---- Do Not Disturb ------------------------------------------------
        val dndActive = sources.doNotDisturbActive()
        items += HealthItem(
            id = HealthIds.DND,
            title = R.string.health_dnd_title,
            why = R.string.health_dnd_why,
            level = if (dndActive) HealthLevel.BLOCKED else HealthLevel.OK,
            fixLabel = if (dndActive) R.string.health_fix_open_settings else null,
            settingsAction = if (dndActive) SettingsAction.DND_ACCESS else SettingsAction.NONE,
        )

        // ---- Background survival ------------------------------------------
        val exempt = sources.batteryExempt()
        items += HealthItem(
            id = HealthIds.BATTERY,
            title = R.string.health_battery_title,
            why = R.string.health_battery_why,
            level = if (exempt) HealthLevel.OK else HealthLevel.WARN,
            fixLabel = if (exempt) null else R.string.health_fix_fix,
            settingsAction = if (exempt) SettingsAction.NONE else SettingsAction.BATTERY_OPTIMIZATION,
        )
        if (sources.oemNeedsAutostartHint()) {
            // Only OEMs that actually kill background apps get this row, and
            // only while it could still be the reason something is late.
            items += HealthItem(
                id = HealthIds.AUTOSTART,
                title = R.string.health_autostart_title,
                why = R.string.health_autostart_why,
                level = if (exempt) HealthLevel.OK else HealthLevel.WARN,
                fixLabel = if (exempt) null else R.string.health_fix_open_settings,
                settingsAction = if (exempt) SettingsAction.NONE else SettingsAction.OEM_AUTOSTART,
            )
        }

        // ---- Capturing payments (owner only) -------------------------------
        if (request.detectsPayments) {
            val listener = sources.listenerAccessGranted()
            items += HealthItem(
                id = HealthIds.LISTENER,
                title = R.string.health_listener_title,
                why = R.string.health_listener_why,
                level = if (listener) HealthLevel.OK else HealthLevel.BLOCKED,
                fixLabel = if (listener) null else R.string.health_fix_turn_on,
                settingsAction = if (listener) {
                    SettingsAction.NONE
                } else {
                    SettingsAction.NOTIFICATION_LISTENER
                },
            )
            val paymentApp = sources.paymentAppInstalled()
            items += HealthItem(
                id = HealthIds.PAYMENT_APP,
                title = R.string.health_payment_app_title,
                why = R.string.health_payment_app_why,
                level = if (paymentApp) HealthLevel.OK else HealthLevel.WARN,
                fixLabel = if (paymentApp) null else R.string.health_fix_install,
                // Installing another app is not a system-Settings fix, and
                // appDetails() would open PayVoice's OWN info page — a dead
                // end for a missing Google Pay.
                settingsAction = if (paymentApp) SettingsAction.NONE else SettingsAction.PLAY_STORE_APP,
            )
        }

        // ---- Speaking ------------------------------------------------------
        val engine = sources.ttsEngineInstalled()
        if (!engine) {
            items += HealthItem(
                id = HealthIds.TTS,
                title = R.string.health_tts_title,
                why = R.string.health_tts_why,
                level = HealthLevel.BLOCKED,
                fixLabel = R.string.health_fix_install_voice,
                settingsAction = SettingsAction.TTS_INSTALL_DATA,
            )
        } else {
            val voice = sources.ttsVoiceAvailable(request.languageTag)
            items += HealthItem(
                id = HealthIds.VOICE,
                title = R.string.health_voice_title,
                why = R.string.health_voice_why,
                level = if (voice) HealthLevel.OK else HealthLevel.BLOCKED,
                fixLabel = if (voice) null else R.string.health_fix_install_voice,
                settingsAction = if (voice) SettingsAction.NONE else SettingsAction.TTS_INSTALL_DATA,
            )
        }

        val maxVolume = sources.maxNotificationVolume()
        if (maxVolume > 0) {
            val volume = sources.notificationVolume()
            items += HealthItem(
                id = HealthIds.VOLUME,
                title = R.string.health_volume_title,
                why = R.string.health_volume_why,
                level = if (volume > 0) HealthLevel.OK else HealthLevel.BLOCKED,
                fixLabel = if (volume > 0) null else R.string.health_fix_open_volume,
                settingsAction = if (volume > 0) SettingsAction.NONE else SettingsAction.VOLUME,
            )
        }

        // ---- Reachability --------------------------------------------------
        val online = sources.online()
        items += HealthItem(
            id = HealthIds.INTERNET,
            title = R.string.health_internet_title,
            why = R.string.health_internet_why,
            level = if (online) HealthLevel.OK else HealthLevel.BLOCKED,
            fixLabel = if (online) null else R.string.health_fix_open_settings,
            settingsAction = if (online) SettingsAction.NONE else SettingsAction.WIRELESS,
        )
        val playServices = sources.playServicesAvailable()
        items += HealthItem(
            id = HealthIds.PLAY_SERVICES,
            title = R.string.health_play_services_title,
            why = R.string.health_play_services_why,
            level = if (playServices) HealthLevel.OK else HealthLevel.BLOCKED,
            fixLabel = null, // nothing in Settings repairs missing Play services
            settingsAction = SettingsAction.NONE,
        )

        // ---- Remote alerts -------------------------------------------------
        val fcm = sources.fcmTokenRegistered()
        items += HealthItem(
            id = HealthIds.FCM,
            title = R.string.health_fcm_title,
            why = R.string.health_fcm_why,
            level = if (fcm) HealthLevel.OK else HealthLevel.WARN,
            fixLabel = null, // the app re-registers on its own
            settingsAction = SettingsAction.NONE,
        )
        val session = sources.supabaseSessionValid()
        items += HealthItem(
            id = HealthIds.SESSION,
            title = R.string.health_session_title,
            why = R.string.health_session_why,
            level = if (session) HealthLevel.OK else HealthLevel.WARN,
            fixLabel = null,
            settingsAction = SettingsAction.NONE,
        )

        // ---- Employee pairing ---------------------------------------------
        if (!request.detectsPayments) {
            items += HealthItem(
                id = HealthIds.PAIRING,
                title = R.string.health_pairing_title,
                why = R.string.health_pairing_why,
                level = if (request.paired) HealthLevel.OK else HealthLevel.BLOCKED,
                fixLabel = if (request.paired) null else R.string.health_fix_connect,
                inAppAction = if (request.paired) InAppAction.NONE else InAppAction.PAIR_DEVICE,
            )
        }

        return items
    }
}

package com.vivekray898.payvoice.core.health

import androidx.annotation.StringRes
import com.vivekray898.payvoice.core.remote.DeviceRole

/**
 * Severity of a single health check.
 *
 * Ordered so callers can sort by severity without a comparator:
 * `BLOCKED` stops payments or announcements outright, `WARN` degrades them.
 */
enum class HealthLevel { OK, WARN, BLOCKED }

/**
 * A system screen a fix button should open. Resolved to an intent chain by
 * [SettingsLauncher] — the checker never touches Android APIs itself, which is
 * what makes it unit-testable.
 */
enum class SettingsAction {
    /** POST_NOTIFICATIONS runtime dialog on 33+, app notification settings below. */
    POST_NOTIFICATIONS,
    APP_NOTIFICATIONS,
    CHANNEL_NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    /**
     * Play-safe: opens the *list* of apps with battery toggles. This is the
     * default because `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs a
     * declared permission form on Google Play.
     */
    BATTERY_OPTIMIZATION,
    /** The direct exemption dialog. Only behind an explicit user action. */
    BATTERY_OPTIMIZATION_DIRECT,
    DND_ACCESS,
    TTS_OUTPUT,
    TTS_INSTALL_DATA,
    VOLUME,
    WIRELESS,
    APP_DETAILS,
    /**
     * Play Store listing for a specific package. Distinct from
     * [APP_DETAILS]: a missing *other* app cannot be repaired from PayVoice's
     * own app-info screen, and sending the user there told them to do
     * something that could not fix the problem.
     */
    PLAY_STORE_APP,
    /** Best-effort OEM autostart page, one file to maintain. */
    OEM_AUTOSTART,
    NONE,
}

/** Fixes the app performs itself rather than handing off to Settings. */
enum class InAppAction { PAIR_DEVICE, OPEN_HEALTH, NONE }

/**
 * One row of the health checklist.
 *
 * @param id stable machine id — used as the LazyColumn key and in tests.
 * @param title short, plain, negative-state phrased ("Notifications are off").
 * @param why one complete sentence explaining the consequence.
 * @param fixLabel `null` renders no button (nothing the user can do here).
 */
data class HealthItem(
    val id: String,
    @StringRes val title: Int,
    @StringRes val why: Int,
    val level: HealthLevel,
    @StringRes val fixLabel: Int? = null,
    val settingsAction: SettingsAction = SettingsAction.NONE,
    val inAppAction: InAppAction = InAppAction.NONE,
    /** Android runtime permission string, when the fix starts with a dialog. */
    val runtimePermission: String? = null,
) {
    val needsAttention: Boolean get() = level != HealthLevel.OK
}

/** Aggregate shown on the Home banner. */
data class HealthSummary(
    val items: List<HealthItem>,
) {
    val blocked: Int get() = items.count { it.level == HealthLevel.BLOCKED }
    val warnings: Int get() = items.count { it.level == HealthLevel.WARN }
    val healthy: Boolean get() = items.isEmpty() || (blocked == 0 && warnings == 0)
    val attentionCount: Int get() = blocked + warnings

    /** Highest severity present — drives the banner colour. */
    val level: HealthLevel
        get() = when {
            blocked > 0 -> HealthLevel.BLOCKED
            warnings > 0 -> HealthLevel.WARN
            else -> HealthLevel.OK
        }

    /** Severity-ordered, warnings and blockers first, OK rows last. */
    fun ordered(): List<HealthItem> =
        items.sortedBy { if (it.level == HealthLevel.OK) 1 else 0 }
}

/** Everything the checker needs to know about the current device state. */
data class HealthRequest(
    val role: DeviceRole,
    /** BCP-47 tag of the configured announcement voice, e.g. "en-IN". */
    val languageTag: String,
    val paired: Boolean,
) {
    /** The owner phone is the one that captures payments. */
    val detectsPayments: Boolean get() = role == DeviceRole.OWNER
}

/**
 * Notification channel importance, mirroring
 * `android.app.NotificationManager.IMPORTANCE_*` so the checker and its
 * fakes never need the Android SDK on the classpath.
 */
object ChannelImportance {
    const val NONE = 0
    const val MIN = 1
    const val LOW = 2
    const val DEFAULT = 3
    const val HIGH = 4
}

/** Every id the checker can emit, for tests and LazyColumn keys. */
object HealthIds {
    const val NOTIFICATIONS = "notifications"
    const val CHANNEL = "channel"
    const val DND = "dnd"
    const val BATTERY = "battery"
    const val AUTOSTART = "autostart"
    const val LISTENER = "listener"
    const val TTS = "tts"
    const val VOICE = "voice"
    const val VOLUME = "volume"
    const val INTERNET = "internet"
    const val FCM = "fcm"
    const val SESSION = "session"
    const val PLAY_SERVICES = "play-services"
    const val PAYMENT_APP = "payment-app"
    const val PAIRING = "pairing"
}

/** Convenience for tests and previews. */
fun healthSummaryOf(vararg items: HealthItem): HealthSummary = HealthSummary(items.toList())

package com.vivekray898.payvoice.core.settings

import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.remote.DeviceRole

/**
 * User-configurable settings. Defaults follow the spec: HIGH-confidence only,
 * amount+sender style, English, 24h dedup retention, 7-day history.
 */
data class ParentSettings(
    val onboardingComplete: Boolean = false,
    val gpayEnabled: Boolean = true,
    /** Diagnostic capture of unknown-package notifications (local only, opt-in). */
    val captureUnknownPackages: Boolean = false,
    val announceHighConfidenceOnly: Boolean = true,
    val style: AnnouncementStyle = AnnouncementStyle.AMOUNT_SENDER,
    val language: AnnouncementLanguage = AnnouncementLanguage.ENGLISH,
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val dedupRetentionHours: Int = 24,
    val historyRetentionDays: Int = 7,
    /** Device role (spec §1): Owner detects, Employee announces remotely. */
    val role: DeviceRole = DeviceRole.UNSET,
    /** This device's display name in the employee list / pairing flow. */
    val deviceName: String = "",
    /** Master switch for owner→employee remote delivery (spec §21). */
    val remoteAnnouncementsEnabled: Boolean = true,
    /**
     * Show the payment amount and sender on the lock screen.
     *
     * The wake-up notification is VISIBILITY_PUBLIC by default, which renders
     * its full text on the lock screen and on the notification shade of any
     * paired wearable. That is a real shoulder-surfing and shoulder-hearing
     * exposure of someone's finances, so it is a choice rather than a fixed
     * behaviour: off posts the notification as VISIBILITY_PRIVATE, which still
     * wakes the device and still speaks the amount aloud, but shows only
     * "Payment received" until the device is unlocked.
     */
    val showPaymentOnLockScreen: Boolean = true,
)

/** Reactive handle over the settings DataStore. */
interface SettingsRepository {
    val settings: kotlinx.coroutines.flow.StateFlow<ParentSettings>
    suspend fun update(transform: (ParentSettings) -> ParentSettings)
}

package com.vivekray898.payvoice.core.settings

import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle

/**
 * User-configurable settings. Defaults follow the spec: HIGH-confidence only,
 * amount+sender style, English, 24h dedup retention, 7-day history.
 */
data class ParentSettings(
    val onboardingComplete: Boolean = false,
    val gpayEnabled: Boolean = true,
    /** Diagnostic capture of unknown-package notifications (local only, opt-in). */
    val captureUnknownPackages: Boolean = false,
    /** SMS backup capture of bank payment SMS (processed locally only). */
    val smsCaptureEnabled: Boolean = true,
    val announceHighConfidenceOnly: Boolean = true,
    val style: AnnouncementStyle = AnnouncementStyle.AMOUNT_SENDER,
    val language: AnnouncementLanguage = AnnouncementLanguage.ENGLISH,
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val dedupRetentionHours: Int = 24,
    val historyRetentionDays: Int = 7,
)

/** Reactive handle over the settings DataStore. */
interface SettingsRepository {
    val settings: kotlinx.coroutines.flow.StateFlow<ParentSettings>
    suspend fun update(transform: (ParentSettings) -> ParentSettings)
}

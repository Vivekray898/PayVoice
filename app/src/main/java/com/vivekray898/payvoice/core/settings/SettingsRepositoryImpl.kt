package com.vivekray898.payvoice.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.remote.DeviceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "payvoice_settings")

/**
 * DataStore-backed settings. Exposes a StateFlow so reads never touch disk on
 * the notification path; writes go through DataStore edit().
 */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val GPAY = booleanPreferencesKey("gpay_enabled")
        val CAPTURE_UNKNOWN = booleanPreferencesKey("capture_unknown_packages")
        val HIGH_ONLY = booleanPreferencesKey("announce_high_only")
        val STYLE = stringPreferencesKey("announcement_style")
        val LANGUAGE = stringPreferencesKey("announcement_language")
        val RATE = floatPreferencesKey("speech_rate")
        val VOLUME = floatPreferencesKey("speech_volume")
        val DEDUP_HOURS = intPreferencesKey("dedup_retention_hours")
        val HISTORY_DAYS = intPreferencesKey("history_retention_days")
        val ROLE = stringPreferencesKey("device_role")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val REMOTE_ENABLED = booleanPreferencesKey("remote_announcements_enabled")
        val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_payment_on_lock_screen")
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val state = MutableStateFlow(ParentSettings())

    override val settings: StateFlow<ParentSettings> = state

    init {
        scope.launch {
            // Hard requirement: DataStore corruption (or any read failure) must
            // NEVER crash the process — an unhandled exception in this scope
            // kills the app AND the notification listener binder mid-flight
            // (observed as NMS DeadObjectException after Clear Data). On
            // failure the safe defaults stay active; a later successful read
            // still updates state.
            context.dataStore.data
                .runCatching { collect { prefs ->
                    state.value = ParentSettings(
                        onboardingComplete = prefs[Keys.ONBOARDED] ?: false,
                        gpayEnabled = prefs[Keys.GPAY] ?: true,
                        captureUnknownPackages = prefs[Keys.CAPTURE_UNKNOWN] ?: false,
                        announceHighConfidenceOnly = prefs[Keys.HIGH_ONLY] ?: true,
                        style = enumOrDefault(prefs[Keys.STYLE], AnnouncementStyle.AMOUNT_SENDER),
                        language = enumOrDefault(prefs[Keys.LANGUAGE], AnnouncementLanguage.ENGLISH),
                        speechRate = prefs[Keys.RATE] ?: 1.0f,
                        speechVolume = prefs[Keys.VOLUME] ?: 1.0f,
                        dedupRetentionHours = prefs[Keys.DEDUP_HOURS] ?: 24,
                        historyRetentionDays = prefs[Keys.HISTORY_DAYS] ?: 7,
                        role = enumOrDefault(prefs[Keys.ROLE], DeviceRole.UNSET),
                        deviceName = prefs[Keys.DEVICE_NAME].orEmpty(),
                        remoteAnnouncementsEnabled = prefs[Keys.REMOTE_ENABLED] ?: true,
                        showPaymentOnLockScreen = prefs[Keys.SHOW_ON_LOCK_SCREEN] ?: true,
                    )
                } }
                .onFailure {
                    com.vivekray898.payvoice.core.util.DebugLog.w(TAG, "settings read failed (defaults active): ${it.javaClass.simpleName}")
                }
        }
    }

    override suspend fun update(transform: (ParentSettings) -> ParentSettings) {
        val next = transform(state.value)
        // Never let a failed write crash the caller (DataStore IO errors are
        // transient; the in-memory state is already correct).
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[Keys.ONBOARDED] = next.onboardingComplete
                prefs[Keys.GPAY] = next.gpayEnabled
                prefs[Keys.CAPTURE_UNKNOWN] = next.captureUnknownPackages
                prefs[Keys.HIGH_ONLY] = next.announceHighConfidenceOnly
                prefs[Keys.STYLE] = next.style.name
                prefs[Keys.LANGUAGE] = next.language.name
                prefs[Keys.RATE] = next.speechRate
                prefs[Keys.VOLUME] = next.speechVolume
                prefs[Keys.DEDUP_HOURS] = next.dedupRetentionHours
                prefs[Keys.HISTORY_DAYS] = next.historyRetentionDays
                prefs[Keys.ROLE] = next.role.name
                prefs[Keys.DEVICE_NAME] = next.deviceName
                prefs[Keys.REMOTE_ENABLED] = next.remoteAnnouncementsEnabled
                prefs[Keys.SHOW_ON_LOCK_SCREEN] = next.showPaymentOnLockScreen
            }
        }.onFailure {
            com.vivekray898.payvoice.core.util.DebugLog.w(TAG, "settings write failed: ${it.javaClass.simpleName}")
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val TAG = "SettingsRepo"
    }
}

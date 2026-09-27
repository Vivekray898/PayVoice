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
import com.vivekray898.payvoice.core.model.PaymentPackages
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
        val KOTAK = booleanPreferencesKey("kotak_enabled")
        val KOTAK_PKG = stringPreferencesKey("kotak_package_id")
        val CAPTURE_UNKNOWN = booleanPreferencesKey("capture_unknown_packages")
        val HIGH_ONLY = booleanPreferencesKey("announce_high_only")
        val STYLE = stringPreferencesKey("announcement_style")
        val LANGUAGE = stringPreferencesKey("announcement_language")
        val RATE = floatPreferencesKey("speech_rate")
        val VOLUME = floatPreferencesKey("speech_volume")
        val DEDUP_HOURS = intPreferencesKey("dedup_retention_hours")
        val HISTORY_DAYS = intPreferencesKey("history_retention_days")
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val state = MutableStateFlow(ParentSettings())

    override val settings: StateFlow<ParentSettings> = state

    init {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                val kotakPackage = prefs[Keys.KOTAK_PKG].orEmpty()
                // Apply the verified Kotak package globally, including when the
                // process starts from the notification listener (UI never opened).
                PaymentPackages.kotakPackageId = kotakPackage.ifBlank { null }
                state.value = ParentSettings(
                    onboardingComplete = prefs[Keys.ONBOARDED] ?: false,
                    gpayEnabled = prefs[Keys.GPAY] ?: true,
                    kotakEnabled = prefs[Keys.KOTAK] ?: false,
                    kotakPackageId = prefs[Keys.KOTAK_PKG].orEmpty(),
                    captureUnknownPackages = prefs[Keys.CAPTURE_UNKNOWN] ?: false,
                    announceHighConfidenceOnly = prefs[Keys.HIGH_ONLY] ?: true,
                    style = enumOrDefault(prefs[Keys.STYLE], AnnouncementStyle.AMOUNT_SENDER),
                    language = enumOrDefault(prefs[Keys.LANGUAGE], AnnouncementLanguage.ENGLISH),
                    speechRate = prefs[Keys.RATE] ?: 1.0f,
                    speechVolume = prefs[Keys.VOLUME] ?: 1.0f,
                    dedupRetentionHours = prefs[Keys.DEDUP_HOURS] ?: 24,
                    historyRetentionDays = prefs[Keys.HISTORY_DAYS] ?: 7,
                )
            }
        }
    }

    override suspend fun update(transform: (ParentSettings) -> ParentSettings) {
        val next = transform(state.value)
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDED] = next.onboardingComplete
            prefs[Keys.GPAY] = next.gpayEnabled
            prefs[Keys.KOTAK] = next.kotakEnabled
            prefs[Keys.KOTAK_PKG] = next.kotakPackageId
            prefs[Keys.CAPTURE_UNKNOWN] = next.captureUnknownPackages
            prefs[Keys.HIGH_ONLY] = next.announceHighConfidenceOnly
            prefs[Keys.STYLE] = next.style.name
            prefs[Keys.LANGUAGE] = next.language.name
            prefs[Keys.RATE] = next.speechRate
            prefs[Keys.VOLUME] = next.speechVolume
            prefs[Keys.DEDUP_HOURS] = next.dedupRetentionHours
            prefs[Keys.HISTORY_DAYS] = next.historyRetentionDays
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

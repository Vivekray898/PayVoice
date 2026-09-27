package com.vivekray898.payvoice.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vivekray898.payvoice.AppContainer
import com.vivekray898.payvoice.PaymentPipeline
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.database.AnnouncementEntity
import com.vivekray898.payvoice.core.database.CapturedNotificationEntity
import com.vivekray898.payvoice.core.database.DiagnosticEntity
import com.vivekray898.payvoice.core.model.PaymentPackages
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.settings.ParentSettings
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun Application.container(): AppContainer =
    (this as com.vivekray898.payvoice.PayVoiceApp).container

/**
 * Single shared Phase-1 ViewModel: the parent app is deliberately small, so
 * one state holder per screen concern beats premature splitting.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.container()
    private val monitor = DeviceStatusMonitor(app)

    val settings: StateFlow<ParentSettings> = container.settings.settings

    val history: StateFlow<List<AnnouncementEntity>> =
        container.database.announcementDao().recent(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow(monitor.snapshot())
    val status: StateFlow<DeviceStatusMonitor.Snapshot> = _status

    val captured: StateFlow<List<CapturedNotificationEntity>> =
        container.database.capturedNotificationDao().recent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diagnostics: StateFlow<List<DiagnosticEntity>> =
        container.database.diagnosticDao().recent(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _testSpeaking = MutableStateFlow(false)
    val testSpeaking: StateFlow<Boolean> = _testSpeaking

    fun refreshStatus() {
        _status.value = monitor.snapshot()
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.settings.update { it.copy(onboardingComplete = true) } }
    }

    fun setGpayEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.update { it.copy(gpayEnabled = enabled) } }
    }

    fun setKotakEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.update { it.copy(kotakEnabled = enabled) } }
    }

    fun setKotakPackage(pkg: String) {
        PaymentPackages.kotakPackageId = pkg.ifBlank { null }
        viewModelScope.launch { container.settings.update { it.copy(kotakPackageId = pkg) } }
    }

    fun setStyle(style: AnnouncementStyle) {
        viewModelScope.launch { container.settings.update { it.copy(style = style) } }
    }

    fun setLanguage(language: AnnouncementLanguage) {
        viewModelScope.launch { container.settings.update { it.copy(language = language) } }
    }

    fun setHighConfidenceOnly(only: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(announceHighConfidenceOnly = only) }
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { container.settings.update { it.copy(speechRate = rate) } }
    }

    fun setSpeechVolume(volume: Float) {
        viewModelScope.launch { container.settings.update { it.copy(speechVolume = volume) } }
    }

    fun setDedupHours(hours: Int) {
        viewModelScope.launch { container.settings.update { it.copy(dedupRetentionHours = hours) } }
    }

    fun setHistoryDays(days: Int) {
        viewModelScope.launch { container.settings.update { it.copy(historyRetentionDays = days) } }
    }

    fun setCaptureUnknownPackages(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(captureUnknownPackages = enabled) }
        }
    }

    fun clearCaptures() {
        viewModelScope.launch { container.database.capturedNotificationDao().clear() }
    }

    /** Safe startActivity for system-settings deep links; no-ops if absent. */
    fun launchIntent(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    /** Speak the current settings' test announcement (spec §27). */
    fun speakTest() {
        if (_testSpeaking.value) return
        _testSpeaking.value = true
        viewModelScope.launch {
            try {
                container.pipeline.announceTest()
            } finally {
                _testSpeaking.value = false
            }
        }
    }

    /** Debug-only canned notification pushed through the real pipeline. */
    fun simulate(source: PaymentSource) {
        val (title, text) = when (source) {
            PaymentSource.GOOGLE_PAY ->
                "Payment received" to "₹500 received from Rahul Sharma. Upi Ref 512345678901"
            PaymentSource.KOTAK ->
                "Kotak Bank" to "Rs. 1200 credited to your account from RAMESH K Ref no 880123456"
        }
        container.pipeline.simulate(source, title, text)
    }

    fun listenerSettingsIntent() = monitor.notificationListenerSettingsIntent()
    fun batteryIntent() = monitor.batteryOptimizationIntent()
    fun appNotificationIntent() = monitor.appNotificationSettingsIntent()
}

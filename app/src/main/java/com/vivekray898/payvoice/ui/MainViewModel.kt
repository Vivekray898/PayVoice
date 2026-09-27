package com.vivekray898.payvoice.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vivekray898.payvoice.AppContainer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.database.AnnouncementEntity
import com.vivekray898.payvoice.core.database.CapturedNotificationEntity
import com.vivekray898.payvoice.core.database.DiagnosticEntity
import com.vivekray898.payvoice.core.model.PaymentPackages
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.settings.ParentSettings
import com.vivekray898.payvoice.service.messaging.MessagingRepository
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun Application.container(): AppContainer =
    (this as com.vivekray898.payvoice.PayVoiceApp).container

/**
 * Shared Phase-1 ViewModel. All status inspection is async and cached —
 * never executed during composition (fixes the 91-skipped-frames jank).
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.container()
    private val monitor = DeviceStatusMonitor(app)

    val settings: StateFlow<ParentSettings> = container.settings.settings

    val history: StateFlow<List<AnnouncementEntity>> =
        container.database.announcementDao().recent(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow<DeviceStatusMonitor.Snapshot?>(null)
    val status: StateFlow<DeviceStatusMonitor.Snapshot?> = _status

    val captured: StateFlow<List<CapturedNotificationEntity>> =
        container.database.capturedNotificationDao().recent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diagnostics: StateFlow<List<DiagnosticEntity>> =
        container.database.diagnosticDao().recent(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fcm: StateFlow<MessagingRepository.FcmStatus> = container.messaging.status

    /** True when running a debug build (gates SMS test tool UI). */
    val isDebugBuild: Boolean
        get() = (getApplication<Application>().applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** TTS engine status for the setup wizard's Text-to-Speech card. */
    val ttsStatus: StateFlow<com.vivekray898.payvoice.service.tts.AnnouncementSpeaker.Status> =
        container.speaker.status

    private val _testSpeaking = MutableStateFlow(false)
    val testSpeaking: StateFlow<Boolean> = _testSpeaking

    init {
        refreshStatus()
        // FCM registration in the background — never on the UI path.
        viewModelScope.launch { container.messaging.refreshToken() }
    }

    /** Called from onResume via lifecycle observer; off-main, cheap to repeat. */
    fun refreshStatus() {
        viewModelScope.launch { _status.value = monitor.snapshot() }
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.settings.update { it.copy(onboardingComplete = true) } }
    }

    // ---- Setup actions (three separate permission mechanisms) ----

    /** B: POST_NOTIFICATIONS runtime permission is requested from the UI; this
     *  posts the legitimate setup notification once granted. */
    fun postSetupNotification(context: Context): Boolean =
        SetupNotifications.postSetupConfirmation(context)

    /** C: tiered battery fix; never a dead button. */
    fun fixBattery(context: Context): String = monitor.launchBatteryFix().also {
        viewModelScope.launch {
            container.diagnosticDaoSafe()?.insert(
                com.vivekray898.payvoice.core.database.DiagnosticEntity(
                    atMs = System.currentTimeMillis(),
                    tag = "battery",
                    message = "opened tier: $it",
                )
            )
        }
    }

    fun openListenerSettings(context: Context) =
        launch(context, monitor.notificationListenerSettingsIntent())

    fun openAppNotificationSettings(context: Context) =
        launch(context, monitor.appNotificationSettingsIntent())

    /** SMS permission lives in app details on modern Android (dangerous permission). */
    fun openAppDetailsSettings(context: Context) =
        launch(context, monitor.appDetailsIntent())

    fun launchIntent(context: Context, intent: Intent) = launch(context, intent)

    private fun launch(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ---- Source toggles ----

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

    fun setSmsCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.update { it.copy(smsCaptureEnabled = enabled) } }
    }

    /** Phase 19 tool: push a sample SMS through the real pipeline (debug only). */
    fun simulateSms(sender: String, body: String) {
        container.pipeline.simulateSms(sender, body)
    }

    /** Phase 19 tool: run a canned SMS through parser classification only (no TTS). */
    fun classifySmsSample(sender: String, body: String): com.vivekray898.payvoice.core.parser.sms.SmsTransactionClassifier.Result {
        val bank = com.vivekray898.payvoice.core.parser.sms.SmsSenderHints.resolveBank(sender, body)
        return com.vivekray898.payvoice.core.parser.sms.SmsTransactionClassifier.classify(sender, body, bank)
    }

    fun clearCaptures() {
        viewModelScope.launch { container.database.capturedNotificationDao().clear() }
    }

    // ---- Test actions ----

    /** Speak "PayVoice test announcement." — works without Firebase. */
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

    /** Pushes a canned payment notification through the real pipeline. */
    fun simulate(source: PaymentSource) {
        val (title, text) = when (source) {
            PaymentSource.GOOGLE_PAY ->
                "Payment received" to "₹500 received from Rahul Sharma. Upi Ref 512345678901"
            PaymentSource.KOTAK ->
                "Kotak Bank" to "Rs. 1200 credited to your account from RAMESH K Ref no 880123456"
        }
        container.pipeline.simulate(source, title, text)
    }

    /** FCM: refresh registration token (also used by the Reliability screen). */
    fun refreshFcmToken() {
        viewModelScope.launch { container.messaging.refreshToken() }
    }
}

/** Null-safe access for diagnostics logging from the VM. */
private fun AppContainer.diagnosticDaoSafe() =
    runCatching { database.diagnosticDao() }.getOrNull()

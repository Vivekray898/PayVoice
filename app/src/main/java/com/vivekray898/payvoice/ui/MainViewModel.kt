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
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.settings.ParentSettings
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.core.remote.EmployeeDevice
import com.vivekray898.payvoice.core.remote.PairingCode
import com.vivekray898.payvoice.core.remote.PayVoiceAuth
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.service.messaging.MessagingRepository
import com.vivekray898.payvoice.service.notification.ListenerRuntime
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

    /**
     * Live listener state: system grant vs actual binding. Android is the
     * source of truth — DataStore is never consulted for this (hard rule).
     */
    val listenerRuntime: StateFlow<ListenerRuntime> = container.listenerRuntime.state

    /** True when running a debug build (gates SMS test tool UI). */
    val isDebugBuild: Boolean
        get() = (getApplication<Application>().applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // ---- Owner→Employee remote state (spec §3, §13, §32) ----

    val authState: StateFlow<PayVoiceAuth.State> = container.auth.state

    val employees: StateFlow<List<EmployeeDevice>> =
        container.employees.observeEmployees()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Employee side: THIS device's registry record (null = not paired). */
    val ownDevice: StateFlow<EmployeeDevice?> =
        container.employees.observeOwnDevice()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val remoteSendState: StateFlow<RemoteEventSender.SendState> =
        container.remoteSender.lastSendState

    /** Last event hit the dedup store as a duplicate (diagnostics, spec §32). */
    val lastRemoteDuplicate: StateFlow<Boolean?> = container.pipeline.lastDedupWasDuplicate

    private val _pairingCode = MutableStateFlow<PairingCode?>(null)
    val pairingCode: StateFlow<PairingCode?> = _pairingCode

    private val _joinResult = MutableStateFlow<Boolean?>(null)
    val joinResult: StateFlow<Boolean?> = _joinResult

    init {
        // Employee heartbeat: register/refresh this device so the Owner sees
        // a real last-seen time (spec §13). Fire-and-forget.
        if (container.settings.settings.value.role == DeviceRole.EMPLOYEE) {
            container.auth.onReady {
                container.pairing.touchDevice(container.messaging.cachedToken())
            }
        }
    }

    /** Role selection (spec §2). Persisted; changing later is explicit. */
    fun setRole(role: DeviceRole, deviceName: String) {
        viewModelScope.launch {
            container.settings.update {
                it.copy(role = role, deviceName = deviceName.trim().take(40))
            }
            if (role == DeviceRole.EMPLOYEE) {
                container.auth.onReady {
                    container.pairing.touchDevice(container.messaging.cachedToken())
                }
            }
        }
    }

    /** Owner: generate a short-lived single-use pairing code (spec §4). */
    fun generatePairingCode() {
        viewModelScope.launch {
            _pairingCode.value = null
            _pairingCode.value = container.pairing.createPairingCode()
        }
    }

    /** Employee: claim a code and join the owner (spec §4). */
    fun joinOwner(code: String) {
        viewModelScope.launch {
            _joinResult.value = null
            val ownerUid = container.pairing.acceptCode(
                code = code,
                deviceName = container.settings.settings.value.deviceName
                    .ifBlank { android.os.Build.MODEL ?: "Employee Device" },
                fcmToken = container.messaging.cachedToken(),
            )
            _joinResult.value = ownerUid != null
            if (ownerUid != null) {
                container.pairing.touchDevice(container.messaging.cachedToken())
            }
        }
    }

    fun clearJoinResult() {
        _joinResult.value = null
    }

    /** Owner: revoke an employee device (spec §16) — backend-enforced. */
    fun revokeEmployee(employeeUid: String) {
        viewModelScope.launch { container.employees.revoke(employeeUid) }
    }

    /** Owner: send the TEST_ANNOUNCEMENT event to all active employees. */
    fun sendTestToEmployees() {
        viewModelScope.launch { container.employees.sendTestAnnouncement() }
    }

    /** Employee: leave the owner's business (spec §21). */
    fun leaveOwner() {
        viewModelScope.launch { container.pairing.leaveOwner() }
    }

    fun setRemoteAnnouncementsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(remoteAnnouncementsEnabled = enabled) }
        }
    }

    fun setDeviceName(name: String) {
        viewModelScope.launch {
            container.settings.update { it.copy(deviceName = name.trim().take(40)) }
        }
    }

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
        // Re-query Android's actual Notification Access state on every resume
        // (returning from system settings must update instantly).
        container.listenerRuntime.refreshSystemGrant(getApplication())
    }

    /**
     * Supported repair for the stuck-listener state (grant present, binding
     * absent): asks NotificationManagerService to rebind. One request per
     * user action — no loops, no foreground service.
     */
    fun repairListener(context: Context) {
        val runtime = container.listenerRuntime
        runtime.refreshSystemGrant(context)
        val requested = runtime.requestRebind(context)
        viewModelScope.launch {
            container.diagnosticDaoSafe()?.insert(
                com.vivekray898.payvoice.core.database.DiagnosticEntity(
                    atMs = System.currentTimeMillis(),
                    tag = "listener",
                    message = "manual rebind requested=$requested",
                )
            )
        }
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

    /** Pushes a canned GPay notification through the real pipeline (only notification source). */
    fun simulate(source: PaymentSource) {
        val (title, text) =
            "Payment received" to "₹500 received from Rahul Sharma. Upi Ref 512345678901"
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

package com.vivekray898.payvoice.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vivekray898.payvoice.AppContainer
import com.vivekray898.payvoice.core.announce.AnnouncementLanguage
import com.vivekray898.payvoice.core.announce.AnnouncementStyle
import com.vivekray898.payvoice.core.health.AppHealthChecker
import com.vivekray898.payvoice.core.health.AndroidHealthSources
import com.vivekray898.payvoice.core.health.DefaultAppHealthChecker
import com.vivekray898.payvoice.core.health.HealthItem
import com.vivekray898.payvoice.core.health.HealthRequest
import com.vivekray898.payvoice.core.health.HealthSummary
import com.vivekray898.payvoice.core.health.InAppAction
import com.vivekray898.payvoice.core.health.SettingsLauncher
import com.vivekray898.payvoice.core.database.AnnouncementEntity
import com.vivekray898.payvoice.core.database.CapturedNotificationEntity
import com.vivekray898.payvoice.core.database.DiagnosticEntity
import com.vivekray898.payvoice.core.model.PaymentSource
import com.vivekray898.payvoice.core.settings.ParentSettings
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.core.remote.EmployeeDevice
import com.vivekray898.payvoice.core.remote.EmployeeRepository
import com.vivekray898.payvoice.core.remote.PairingCode
import com.vivekray898.payvoice.core.remote.PairingRepository
import com.vivekray898.payvoice.core.remote.PayVoiceAuth
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.service.messaging.MessagingRepository
import com.vivekray898.payvoice.service.notification.ListenerRuntime
import com.vivekray898.payvoice.service.setup.SetupNotifications
import com.vivekray898.payvoice.service.status.DeviceStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** True when running a debug build (gates debug test tool UI). */
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

    /**
     * Join outcome for the employee UI. Tri-state Boolean was a lie: an
     * auth-still-initializing attempt rendered as "expired". The screen now
     * shows an honest "initializing secure session" state while pairing.
     */
    sealed class JoinState {
        object Idle : JoinState()
        object Joining : JoinState()
        data class Success(val ownerUid: String) : JoinState()
        data class Failed(val message: String) : JoinState()
    }

    private val _joinState = MutableStateFlow<JoinState>(JoinState.Idle)
    val joinState: StateFlow<JoinState> = _joinState

    /**
     * Backward-compatible alias for joinState rendered as Boolean?
     * (null = idle/joining, true = success, false = failed).
     */
    @Deprecated("Use joinState for accurate outcomes")
    val joinResult: StateFlow<Boolean?>
        get() = _joinResultAlias
    private val _joinResultAlias = MutableStateFlow<Boolean?>(null)

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

    /**
     * Employee: claim a code and join the owner (spec §4). Waits for the
     * single-flight anonymous session BEFORE the RPC; surfaces the real
     * failure mode instead of a blanket "expired".
     */
    fun joinOwner(code: String) {
        if (_joinState.value is JoinState.Joining) return
        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.pairingStarted()
        val joinStartedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _joinState.value = JoinState.Joining
            _joinResultAlias.value = null
            val result = container.pairing.acceptCode(
                code = code,
                deviceName = container.settings.settings.value.deviceName
                    .ifBlank { android.os.Build.MODEL ?: "Employee Device" },
                fcmToken = container.messaging.cachedToken(),
            )
            when (result) {
                is PairingRepository.ClaimResult.Success -> {
                    _joinState.value = JoinState.Success(result.ownerUid)
                    _joinResultAlias.value = true
                    container.pairing.touchDevice(container.messaging.cachedToken())
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.pairingCompleted(
                        System.currentTimeMillis() - joinStartedAt,
                    )
                }
                is PairingRepository.ClaimResult.Rejected -> {
                    _joinState.value = JoinState.Failed(
                        when (result.reason) {
                            PairingRepository.ClaimResult.Rejected.EXPIRED ->
                                "Pairing code expired. Ask for a new code."
                            PairingRepository.ClaimResult.Rejected.ALREADY_USED ->
                                "Pairing code already used. Ask for a new code."
                            PairingRepository.ClaimResult.Rejected.INVALID,
                            PairingRepository.ClaimResult.Rejected.UNAUTHENTICATED ->
                                "Invalid pairing code. Check it and try again."
                            else -> "Pairing rejected (${result.reason})."
                        },
                    )
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.pairingFailed(
                        when (result.reason) {
                            PairingRepository.ClaimResult.Rejected.EXPIRED ->
                                com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.EXPIRED
                            PairingRepository.ClaimResult.Rejected.ALREADY_USED ->
                                com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.ALREADY_USED
                            PairingRepository.ClaimResult.Rejected.INVALID,
                            PairingRepository.ClaimResult.Rejected.UNAUTHENTICATED,
                            -> com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.INVALID
                            else -> com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.INVALID
                        },
                    )
                    _joinResultAlias.value = false
                }
                PairingRepository.ClaimResult.SessionNotReady -> {
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.pairingFailed(
                        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.UNAUTHENTICATED,
                    )
                    _joinState.value = JoinState.Failed(
                        "Still connecting securely — check internet, then try again.",
                    )
                    _joinResultAlias.value = false
                }
                PairingRepository.ClaimResult.InvalidCode -> {
                    _joinState.value = JoinState.Failed("Invalid pairing code format.")
                    _joinResultAlias.value = false
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.pairingFailed(
                        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.INVALID,
                    )
                }
                PairingRepository.ClaimResult.NetworkError -> {
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.pairingFailed(
                        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.PairingFailure.NETWORK,
                    )
                    _joinState.value = JoinState.Failed(
                        "Network problem reaching the server — try again.",
                    )
                    _joinResultAlias.value = false
                }
            }
        }
    }

    fun clearJoinResult() {
        _joinState.value = JoinState.Idle
        _joinResultAlias.value = null
    }

    /** Owner revoke states — the UI never reports silent success. */
    sealed class RevokeState {
        object Idle : RevokeState()
        data class Success(val employeeUid: String) : RevokeState()
        data class Failed(val message: String) : RevokeState()
    }

    private val _revokeState = MutableStateFlow<RevokeState>(RevokeState.Idle)
    val revokeState: StateFlow<RevokeState> = _revokeState

    /**
     * Owner: revoke an employee device (spec §16) — backend-enforced via the
     * atomic `revoke_employee` RPC (migration 0004) with a counted RLS-
     * filtered UPDATE fallback. "Nothing was updated" surfaces as a failure,
     * never as success. The live employees flow (Realtime/poll) refreshes
     * the list automatically once the row flips.
     */
    fun revokeEmployee(employeeUid: String) {
        viewModelScope.launch {
            when (val r = container.employees.revoke(employeeUid)) {
                is EmployeeRepository.RevokeResult.Success -> {
                    _revokeState.value = RevokeState.Success(employeeUid)
                    com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.employeeRevoked()
                }
                EmployeeRepository.RevokeResult.NotFound ->
                    _revokeState.value = RevokeState.Failed(
                        "Device not removed — it may already be removed, or you no longer own it.",
                    )
                is EmployeeRepository.RevokeResult.Failed ->
                    _revokeState.value = RevokeState.Failed(
                        "Remove failed (${r.reason}) — check connection and try again.",
                    )
            }
        }
    }

    fun clearRevokeState() {
        _revokeState.value = RevokeState.Idle
    }

    /**
     * Owner: send the TEST_ANNOUNCEMENT event to all active employees.
     * Failures surface in the UI state — never silently swallowed (an
     * unsurfaced error on HyperOS looked like a random crash).
     */
    fun sendTestToEmployees() {
        if (_testSendState.value is TestSendState.Sending) return
        viewModelScope.launch {
            _testSendState.value = TestSendState.Sending
            runCatching { container.employees.sendTestAnnouncement() }
                .onSuccess { ok ->
                    _testSendState.value =
                        if (ok) TestSendState.Sent(System.currentTimeMillis())
                        else TestSendState.Failed("Backend rejected or unreachable — check connection")
                }
                .onFailure { e ->
                    _testSendState.value = TestSendState.Failed(
                        "Test announcement failed (${e.javaClass.simpleName}) — local announcements unaffected",
                    )
                }
        }
    }

    sealed class TestSendState {
        object Idle : TestSendState()
        object Sending : TestSendState()
        data class Sent(val atMs: Long) : TestSendState()
        data class Failed(val message: String) : TestSendState()
    }

    private val _testSendState = MutableStateFlow<TestSendState>(TestSendState.Idle)
    val testSendState: StateFlow<TestSendState> = _testSendState

    /**
     * Employee: leave the owner's business (spec §21). The verdict is state,
     * not a fire-and-forget call: leaving is how a phone stops receiving
     * someone else's payments, so a write that did not land must say so
     * instead of leaving the card reading "Live".
     */
    fun leaveOwner() {
        viewModelScope.launch {
            _leaveState.value = when (val ok = container.pairing.leaveOwner()) {
                true -> LeaveState.Done
                false -> LeaveState.Failed(
                    "Couldn't leave — the server didn't accept it. Check connection and try again.",
                )
            }
        }
    }

    fun clearLeaveState() {
        _leaveState.value = LeaveState.Idle
    }

    fun setRemoteAnnouncementsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(remoteAnnouncementsEnabled = enabled) }
        }
    }

    /** Lock-screen privacy: whether the amount/sender is shown when locked. */
    fun setShowPaymentOnLockScreen(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(showPaymentOnLockScreen = enabled) }
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

    /** Employee leave-a-business verdict — mirrors [RevokeState] on the owner side. */
    sealed class LeaveState {
        object Idle : LeaveState()
        object Done : LeaveState()
        data class Failed(val message: String) : LeaveState()
    }

    private val _leaveState = MutableStateFlow<LeaveState>(LeaveState.Idle)
    val leaveState: StateFlow<LeaveState> = _leaveState

    private val _testSpeaking = MutableStateFlow(false)
    val testSpeaking: StateFlow<Boolean> = _testSpeaking

    // ---- Health / permission checklist (Step 3) ---------------------------
    //
    // ONE list drives the Home banner and the Settings checklist — the two
    // previously disagreed because ReliabilityScreen kept its own local
    // `data class Check`. Re-evaluated on every ON_RESUME (via
    // refreshStatus) so a fix made in system Settings appears immediately.

    private val _health = MutableStateFlow<HealthSummary?>(null)
    val health: StateFlow<HealthSummary?> = _health

    private val _healthLoading = MutableStateFlow(true)
    val healthLoading: StateFlow<Boolean> = _healthLoading

    private val healthChecker: AppHealthChecker by lazy {
        DefaultAppHealthChecker(
            AndroidHealthSources(
                context = getApplication(),
                // `tokenAvailable` is true only for the lifetime of the process that
                // fetched the token. Health runs on ON_RESUME, which on a cold
                // start is *before* refreshToken() finishes — so asking only
                // the in-memory flag reported "this device isn't registered
                // for alerts" on every launch until the fetch completed, and
                // forever if it failed. The encrypted copy on disk is the
                // honest signal: it is what we would actually send.
                fcmTokenRegisteredSource = {
                    container.messaging.status.value.tokenAvailable ||
                        !container.messaging.cachedToken().isNullOrBlank()
                },
                sessionValid = { container.auth.state.value is PayVoiceAuth.State.READY },
            ),
        )
    }

    /** Current [HealthRequest], derived from the flows the checker needs. */
    private fun healthRequest() = HealthRequest(
        role = settings.value.role,
        languageTag = settings.value.language.ttsLocaleTag,
        paired = ownDevice.value?.isActive == true,
    )

    /**
     * Re-run every check. Cheap reads, but IPC — so off the main thread.
     * A failure keeps the previous list rather than blanking the banner:
     * a stale verdict is better than an empty one that says "all good".
     */
    fun refreshHealth() {
        viewModelScope.launch {
            if (_health.value == null) _healthLoading.value = true
            val result = withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { healthChecker.check(healthRequest()) }
            }
            result.onSuccess { _health.value = HealthSummary(it) }
            _healthLoading.value = false
        }
    }

    /**
     * Execute a health row's fix. In-app actions (pairing, checklist) are
     * navigation and are handled by the caller before reaching here.
     */
    fun applyHealthFix(context: Context, item: HealthItem) {
        if (item.inAppAction != InAppAction.NONE) return
        // No new analytics event: docs/ANALYTICS.md is a closed catalogue of
        // structural-only events, and "which fix button was pressed" is user
        // content that does not belong in it.
        SettingsLauncher(context).launch(item.settingsAction)
    }

    init {
        refreshStatus()
        // FCM registration in the background — never on the UI path. The
        // whole block (Firebase init reads the google-services.json ASSET,
        // i.e. disk I/O) runs on IO so startup never pays for it (the
        // "Skipped 365 frames" jank signature).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val token = container.messaging.refreshToken().getOrNull() ?: return@launch
                container.devices.registerDevice(
                    fcmToken = token,
                    deviceName = container.settings.settings.value.deviceName
                        .ifBlank { android.os.Build.MODEL ?: "Device" },
                )
            }
        }
        // The health checklist used to run once, from onResume, which on a cold
        // start is BEFORE the token fetch above finishes — so it read "this
        // device isn't registered for alerts" and never re-ran, leaving a
        // permanent false alarm on a device that had registered fine. Re-check
        // when the token verdict actually changes.
        viewModelScope.launch {
            container.messaging.status
                .map { it.tokenAvailable }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshHealth() }
        }
        // Same reasoning for the pairing verdict. HealthRequest.paired is a
        // SNAPSHOT of ownDevice taken when the checklist ran, so before this
        // a successful claim left the banner reading "This phone isn't
        // connected to a business" next to a card that said Live — the
        // checklist only re-ran on resume or on an FCM token change, neither
        // of which happens when you pair. Re-check when the verdict actually
        // flips, so pairing clears the item and leaving restores it.
        viewModelScope.launch {
            ownDevice
                .map { it?.isActive == true }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshHealth() }
        }
    }

    /** Called from onResume via lifecycle observer; off-main, cheap to repeat. */
    fun refreshStatus() {
        viewModelScope.launch { _status.value = monitor.snapshot() }
        // Re-query Android's actual Notification Access state on every resume
        // (returning from system settings must update instantly).
        container.listenerRuntime.refreshSystemGrant(getApplication())
        // Same trigger for the health checklist — the banner must refresh the
        // moment the user comes back from fixing something.
        refreshHealth()
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
        com.vivekray898.payvoice.core.analytics.PayVoiceAnalytics.batteryExempt(
            alreadyExempt = status.value?.batteryExempt == true,
        )
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
        viewModelScope.launch {
            val token = container.messaging.refreshToken().getOrNull() ?: return@launch
            container.devices.registerDevice(
                fcmToken = token,
                deviceName = container.settings.settings.value.deviceName
                    .ifBlank { android.os.Build.MODEL ?: "Device" },
            )
        }
    }
}

/** Null-safe access for diagnostics logging from the VM. */
private fun AppContainer.diagnosticDaoSafe() =
    runCatching { database.diagnosticDao() }.getOrNull()

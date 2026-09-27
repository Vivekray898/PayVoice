package com.vivekray898.payvoice.service.notification

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime connection state of the [PayVoiceNotificationListener].
 *
 * Two states that must NEVER be conflated:
 *  - [ListenerRuntime.systemGrant]: the persisted grant in
 *    `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS`. It survives Clear Data.
 *  - [ListenerRuntime.connected]: the live binding Android actually made to
 *    this process. Android owns it; after Clear Data/force-stop the platform
 *    frequently does not re-bind even though the grant is still present.
 *
 * `systemGrant == true && connected == false` is the exact signature of the
 * post-Clear-Data regression (and the NMS DeadObjectException entries: the
 * system still holds a stale binder for a dead binding).
 *
 * Repair uses ONLY the supported mechanism: [NotificationManager.requestRebind]
 * (API 24+) — no polling, no foreground service, no manual startService.
 */
data class ListenerRuntime(
    val systemGrant: Boolean = false,
    val connected: Boolean = false,
    val lastConnectedAtMs: Long = 0,
    val lastDisconnectedAtMs: Long = 0,
    val lastPostedAtMs: Long = 0,
    val lastPostedPackage: String? = null,
    val rebindRequestedAtMs: Long = 0,
) {
    /** System says enabled but no live binding — the Clear Data regression. */
    val mismatch: Boolean get() = systemGrant && !connected
}

class ListenerRuntimeState(
    private val componentName: ComponentName,
) {

    private val _state = MutableStateFlow(ListenerRuntime())
    val state: StateFlow<ListenerRuntime> = _state

    // ---- Service lifecycle hooks (called from the service callbacks) ----

    fun onConnected() = _state.update {
        it.copy(connected = true, lastConnectedAtMs = System.currentTimeMillis())
    }

    fun onDisconnected() = _state.update {
        it.copy(connected = false, lastDisconnectedAtMs = System.currentTimeMillis())
    }

    /** Package names + timestamps only (logging policy: never content). */
    fun onNotificationPosted(packageName: String, atMs: Long) = _state.update {
        it.copy(lastPostedAtMs = atMs, lastPostedPackage = packageName)
    }

    /** Refresh the persisted-grant side from the real system setting. */
    fun refreshSystemGrant(context: Context) {
        val granted = isListenerComponentEnabled(context, componentName)
        _state.update { it.copy(systemGrant = granted) }
    }

    // ---- Supported repair path ----

    /**
     * Asks NotificationManagerService to unbind + rebind the listener via the
     * platform's static [NotificationListenerService.requestRebind] — the
     * documented mechanism for a stuck listener binding (requested at most
     * once per detection; never a loop).
     */
    fun requestRebind(context: Context): Boolean = runCatching {
        NotificationListenerService.requestRebind(componentName)
        _state.update { it.copy(rebindRequestedAtMs = System.currentTimeMillis()) }
        true
    }.getOrDefault(false)

    /**
     * Called once at process start (PayVoiceApp.onCreate, background thread).
     * If the grant exists but the binding has not come up within a grace
     * period, request exactly one rebind. One-shot — not a retry loop.
     */
    fun scheduleStartupRepair(context: Context) {
        refreshSystemGrant(context)
        if (!_state.value.systemGrant) return
        Handler(Looper.getMainLooper()).postDelayed(
            {
                refreshSystemGrant(context)
                val st = _state.value
                if (st.systemGrant && !st.connected) {
                    requestRebind(context)
                }
            },
            STARTUP_REPAIR_GRACE_MS,
        )
    }

    companion object {

        private const val STARTUP_REPAIR_GRACE_MS = 8_000L

        /**
         * Authoritative Notification Access check against the
         * `enabled_notification_listeners` secure setting, matching THIS exact
         * component (not just the package). DataStore is never consulted.
         *
         * The setting key is spelled literally: newer SDK stubs hide the
         * Settings.Secure constant from third-party apps, but the setting
         * itself remains the source of truth at runtime.
         */
        fun isListenerComponentEnabled(context: Context, component: ComponentName): Boolean {
            val flat = runCatching {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull() ?: return false
            val targets = setOf(component.flattenToString(), component.flattenToShortString())
            return flat.split(':')
                .map { it.trim() }
                .any { it in targets || ComponentName.unflattenFromString(it) == component }
        }

        fun forThisApp(context: Context): ListenerRuntimeState = ListenerRuntimeState(
            ComponentName(context, PayVoiceNotificationListener::class.java),
        )
    }
}

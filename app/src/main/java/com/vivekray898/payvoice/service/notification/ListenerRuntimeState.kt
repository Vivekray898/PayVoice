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
    /** Instance identity of the service instance that owns the live binding. */
    val connectedInstanceId: String? = null,
) {
    /** System says enabled but no live binding — the Clear Data regression. */
    val mismatch: Boolean get() = systemGrant && !connected
}

class ListenerRuntimeState(
    private val componentName: ComponentName,
    private val isDebugBuild: Boolean = false,
) {

    private val _state = MutableStateFlow(ListenerRuntime())
    val state: StateFlow<ListenerRuntime> = _state

    // ---- Service lifecycle hooks (called from the service callbacks) ----

    /**
     * Records a live binding for [instanceId]. Returns the identity of a
     * DIFFERENT instance that previously owned the binding (a system rebind
     * superseding it) or null when this is the same/first connection. The
     * service logs it so "duplicate onListenerConnected" logs tell us WHICH
     * duplicate happened without guesswork.
     */
    fun onConnected(instanceId: String): String? {
        var superseded: String? = null
        _state.update {
            if (it.connectedInstanceId != null && it.connectedInstanceId != instanceId) {
                superseded = it.connectedInstanceId
            }
            it.copy(
                connected = true,
                lastConnectedAtMs = System.currentTimeMillis(),
                connectedInstanceId = instanceId,
            )
        }
        return superseded
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
     * Debug builds log every decision point so the repair path is visible
     * ("repair ran but did nothing" must be distinguishable from "repair
     * never ran").
     */
    fun scheduleStartupRepair(context: Context) {
        refreshSystemGrant(context)
        if (!_state.value.systemGrant) {
            debugLog("startup-repair skipped (no system grant)")
            return
        }
        debugLog("startup-repair scheduled (grant present, connected=" + _state.value.connected + ")")
        Handler(Looper.getMainLooper()).postDelayed(
            {
                refreshSystemGrant(context)
                val st = _state.value
                if (st.systemGrant && !st.connected) {
                    val ok = requestRebind(context)
                    debugLog("startup-repair FIRED grant=true connected=false rebind=$ok")
                } else {
                    debugLog(
                        "startup-repair no-op grant=" + st.systemGrant +
                            " connected=" + st.connected,
                    )
                }
            },
            STARTUP_REPAIR_GRACE_MS,
        )
    }

    private fun debugLog(message: String) {
        if (isDebugBuild) {
            android.util.Log.d("ListenerRuntime", message)
        }
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
            isDebugBuild = (context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )
    }
}

package com.vivekray898.payvoice.service.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.vivekray898.payvoice.PayVoiceApp
import com.vivekray898.payvoice.core.model.PaymentSource
import kotlinx.coroutines.launch

/**
 * Notification listener (spec §4, §38, §41).
 *
 * Hard rules enforced here:
 *  - Only explicitly configured payment app packages are ever processed.
 *    Every other notification is ignored at the door.
 *  - onNotificationPosted does no work on the calling thread beyond reading
 *    a few extras and dispatching to the pipeline scope.
 *  - Raw content stays on-device; lifecycle logs carry package + timestamp
 *    only (never notification text).
 *
 * Lifecycle (post-Clear-Data fix): Android owns this service. When the
 * platform tears the binding down (Clear Data, process death), it frequently
 * does NOT re-bind even though Notification Access is still granted — the
 * exact signature of the DeadObjectException regression. [onListenerDisconnected]
 * handles this with the supported [NotificationManager.requestRebind] via
 * [ListenerRuntimeState]; no polling, no foreground service, no startService.
 */
class PayVoiceNotificationListener : NotificationListenerService() {

    private val container by lazy { (applicationContext as PayVoiceApp).container }
    private val runtime: ListenerRuntimeState by lazy { container.listenerRuntime }

    /** Distinguishes "same instance re-entered" from "system created a second instance". */
    private val instanceId = "${Integer.toHexString(System.identityHashCode(this))}/pid=${android.os.Process.myPid()}"

    /** Per-instance connect latch: initialization must be idempotent (spec §5). */
    @Volatile
    private var connectedOnce = false

    override fun onCreate() {
        super.onCreate()
        log("onCreate instance=$instanceId thread=${Thread.currentThread().name}")
        runtime.refreshSystemGrant(this)
    }

    override fun onListenerConnected() {
        // Duplicate-callback diagnosis (Bad key / doubled onNotificationPosted
        // signature): the FIRST task is to say WHICH kind of duplicate this is.
        //  - same instance re-connected → genuinely duplicated callback; skip
        //    re-init (snapshot re-dispatch would double-fire the pipeline).
        //  - a DIFFERENT instance connecting → the system rebound the service
        //    (allowed; the old instance is obsolete and Android will destroy
        //    it). The new instance must initialize; the runtime keeps track.
        if (connectedOnce) {
            log("DUPLICATE onListenerConnected ignored instance=$instanceId")
            return
        }
        connectedOnce = true
        val superseded = runtime.onConnected(instanceId)
        log(
            "onListenerConnected instance=$instanceId thread=${Thread.currentThread().name} " +
                "superseded=" + (superseded?.let { "instance=$it" } ?: "none"),
        )
        // Warm the TTS engine off the bind path so the first payment does not
        // pay engine cold-start. Fire-and-forget; never blocks the callback.
        container.applicationScope.launch { runCatching { container.speaker.warmUp() } }

        // Process only the current snapshot once. Reposts from the snapshot are
        // collapse-handled by the dedup store — never re-announced.
        val snapshot = runCatching { activeNotifications }.getOrNull() ?: return
        snapshot.forEach { sbn -> dispatchIfWhitelisted(sbn) }
    }

    override fun onListenerDisconnected() {
        // Supported rebind path: ask NMS to rebind us (spec: no retry loops,
        // no FGS, no manual startService — this is the platform's mechanism).
        // NEVER called as part of successful initialization: requestRebind is
        // only for the genuine disconnected recovery path.
        log("onListenerDisconnected instance=$instanceId → requestRebind")
        runtime.onDisconnected()
        runtime.requestRebind(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        dispatchIfWhitelisted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        log("onNotificationRemoved package=${sbn.packageName}")
    }

    override fun onDestroy() {
        // Must NOT touch shared infrastructure (TTS engine, DB, scopes) —
        // destroy is routine rebinding; app-scoped singletons outlive us.
        // Cleanup resets this instance's latch so a future re-instance starts
        // clean; the shared runtime reflects the lost binding.
        log("onDestroy instance=$instanceId hadConnected=$connectedOnce")
        runtime.onDisconnected()
        super.onDestroy()
    }

    private fun dispatchIfWhitelisted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        runtime.onNotificationPosted(pkg, sbn.postTime)
        // Generic package diagnostic only — never a bank-app payment source.
        // A com.kotak811 (or any bank app) notification fails this lookup and
        // is ignored (or locally captured in diagnostics mode).
        if (isPlatformDuplicate(sbn)) {
            // system_server double-delivers the same sbn milliseconds apart
            // (BoundServiceSession "Bad key" binder bug, seen with rapidly
            // updating packages). The platform call cannot be prevented; the
            // processing below this line stays single-shot per sbn.
            log("platform duplicate delivery dropped package=$pkg id=${sbn.id} postTime=${sbn.postTime}")
            return
        }
        log("onNotificationPosted package=$pkg")
        val settings = container.settings.settings.value
        val source = PaymentSource.fromPackage(pkg)

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val summary = buildExtrasSummary(extras)

        if (source == null) {
            // Opt-in local capture used to verify unknown packages. Off by default.
            if (settings.captureUnknownPackages) {
                container.pipeline.captureOnly(
                    packageName = pkg,
                    title = title,
                    text = text,
                    bigText = bigText,
                    subText = subText,
                    notificationId = sbn.id,
                    postedAtMs = sbn.postTime,
                    extrasSummary = summary,
                )
            }
            return
        }

        // GPay is the only notification source; there is no bank-app toggle.
        if (!settings.gpayEnabled) return

        container.pipeline.handleNotification(
            packageName = pkg,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            notificationId = sbn.id,
            postedAtMs = sbn.postTime,
            extrasSummary = summary,
        )
    }

    /** Short, capped summary of standard extras for the diagnostics screen. */
    private fun buildExtrasSummary(extras: Bundle): String = runCatching {
        extras.keySet()
            .filter { it.startsWith("android.") }
            .sorted()
            .take(10)
            .joinToString(" | ") { key ->
                val value = extras.get(key)?.toString()?.take(48).orEmpty()
                "$key=$value"
            }
    }.getOrDefault("")

    /**
     * True for an identical (pkg, id, tag, postTime) sbn seen within the
     * dedup window — the platform's double-delivery signature. Recent keys
     * are kept in a small bounded map; distinct posts (different id/time)
     * always pass. This does NOT deduplicate real notification updates:
     * those carry new postTime/id values.
     */
    private fun isPlatformDuplicate(sbn: StatusBarNotification): Boolean {
        val key = "${sbn.packageName}/${sbn.id}/${sbn.tag ?: "-"}/${sbn.postTime}"
        val now = System.currentTimeMillis()
        synchronized(recentDeliveries) {
            // Opportunistic expiry of stale entries.
            if (recentDeliveries.size > MAX_RECENT) {
                recentDeliveries.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
            }
            val last = recentDeliveries[key]
            recentDeliveries[key] = now
            return last != null && now - last <= DEDUP_WINDOW_MS
        }
    }

    private val recentDeliveries = HashMap<String, Long>()

    private companion object {
        private const val TAG = "PayVoiceNLS"
        private const val DEDUP_WINDOW_MS = 1_000L
        private const val MAX_RECENT = 64
    }

    private fun log(message: String) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message)
        }
    }
}

package com.vivekray898.payvoice.service.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.vivekray898.payvoice.PayVoiceApp
import com.vivekray898.payvoice.core.model.PaymentPackages
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

    override fun onCreate() {
        super.onCreate()
        log("onCreate")
        runtime.refreshSystemGrant(this)
    }

    override fun onListenerConnected() {
        log("onListenerConnected")
        runtime.onConnected()
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
        log("onListenerDisconnected → requestRebind")
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
        log("onDestroy")
        runtime.onDisconnected()
        super.onDestroy()
    }

    private fun dispatchIfWhitelisted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        runtime.onNotificationPosted(pkg, sbn.postTime)
        log("onNotificationPosted package=$pkg")
        val settings = container.settings.settings.value
        val source = PaymentSource.fromPackage(pkg) ?: run {
            // Kotak candidate not yet pinned: accept the verified capture or the
            // installed com.kotak811 candidate (verified against PackageManager).
            val kotak = PaymentPackages.resolveKotakPackage { candidate ->
                runCatching { packageManager.getPackageInfo(candidate, 0) }.isSuccess
            }
            if (kotak != null && pkg == kotak) PaymentSource.KOTAK else null
        }

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

        val enabled = when (source) {
            PaymentSource.GOOGLE_PAY -> settings.gpayEnabled
            PaymentSource.KOTAK -> settings.kotakEnabled
        }
        if (!enabled) return

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

    private fun log(message: String) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "PayVoiceNLS"
    }
}

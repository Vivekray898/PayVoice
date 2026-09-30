package com.vivekray898.payvoice.core.analytics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.vivekray898.payvoice.core.util.DebugLog

/**
 * Single entry point for Analytics events (user-approved scope). All calls
 * are fire-and-forget, best-effort, and never carry payment content.
 *
 * Rules enforced here:
 *  - Debug builds are disabled at init, so events never leave debug sessions.
 *  - Every event name and every parameter key is declared as a constant so
 *    accidental free-form logging is impossible.
 *  - No amount, sender, UPI ID, account number, or raw text is ever passed
 *    in. Only structural signals (counts, booleans, enum names, stage names).
 *  - Uses [ApplicationInfo.FLAG_DEBUGGABLE] (not BuildConfig — this project
 *    does not generate BuildConfig) and [FirebaseAnalytics.getInstance]
 *    directly (no ktx): the object stays JVM-unit-test-safe and calls before
 *    init or in JVM tests are no-ops that never throw or load Android classes
 *    beyond the imports.
 *
 * See docs/ANALYTICS.md for the full event catalog. Any new event must be
 * added there BEFORE the code commit.
 */
object PayVoiceAnalytics {

    private var instance: FirebaseAnalytics? = null

    /** Set by [init] (debug builds are disabled at init, docs/ANALYTICS.md). */
    private var debuggableFlag: Boolean = false

    /** Called once from PayVoiceApp.onCreate, AFTER initFirebase(). Idempotent. */
    fun init(context: Context) {
        if (instance != null) return
        debuggableFlag =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        runCatching {
            val fa = FirebaseAnalytics.getInstance(context)
            fa.setAnalyticsCollectionEnabled(!debuggableFlag)
            fa.setUserId(null) // we do NOT identify users
            fa.setUserProperty(USER_PROP_ROLE, null)
            instance = fa
            DebugLog.d(TAG, "Analytics initialized (enabled=${!debuggableFlag})")
        }.onFailure {
            android.util.Log.e(TAG, "Analytics init failed", it)
        }
    }

    // ---- App lifecycle ----

    fun appOpened() = log(Event.APP_OPENED, null)

    fun appColdStarted(durationMs: Long) = log(Event.APP_COLD_STARTED) {
        putLong(Param.COLD_START_MS, durationMs)
    }

    // ---- Permissions ----

    fun notificationPermission(result: PermissionResult) = log(Event.NOTIF_PERMISSION) {
        putString(Param.RESULT, result.name)
    }

    fun smsPermission(result: PermissionResult) = log(Event.SMS_PERMISSION) {
        putString(Param.RESULT, result.name)
    }

    fun batteryExempt(alreadyExempt: Boolean) = log(Event.BATTERY_EXEMPT) {
        putString(Param.RESULT, if (alreadyExempt) "already" else "prompted")
    }

    // ---- Pairing ----

    fun pairingStarted() = log(Event.PAIRING_STARTED, null)

    fun pairingCompleted(durationMs: Long) = log(Event.PAIRING_COMPLETED) {
        putLong(Param.DURATION_MS, durationMs)
    }

    fun pairingFailed(reason: PairingFailure) = log(Event.PAIRING_FAILED) {
        putString(Param.REASON, reason.name)
    }

    fun employeeRevoked() = log(Event.EMPLOYEE_REVOKED, null)

    // ---- Payments (structural only — never amounts/senders) ----

    fun paymentCapturedLocal() = log(Event.PAYMENT_CAPTURED_LOCAL, null)

    fun paymentAnnouncedLocal(latencyMs: Long) = log(Event.PAYMENT_ANNOUNCED_LOCAL) {
        putLong(Param.LATENCY_MS, latencyMs)
    }

    fun remoteDeliverySucceeded(latencyMs: Long) = log(Event.REMOTE_DELIVERY_SUCCEEDED) {
        putLong(Param.LATENCY_MS, latencyMs)
    }

    fun remoteDeliveryFailed(stage: String) = log(Event.REMOTE_DELIVERY_FAILED) {
        putString(Param.STAGE, stage)
    }

    fun crossChannelSuppressed(source: String) = log(Event.CROSS_CHANNEL_SUPPRESSED) {
        putString(Param.SOURCE, source)
    }

    // ---- Errors (non-fatal) ----

    fun nonFatalError(area: ErrorArea, errorClass: String) = log(Event.NON_FATAL_ERROR) {
        putString(Param.AREA, area.name)
        putString(Param.ERROR_CLASS, errorClass) // class name only — never the message
    }

    // ---- Internal ----

    private fun log(name: String, block: (Bundle.() -> Unit)?) {
        val fa = instance ?: return
        runCatching {
            val params = Bundle()
            block?.invoke(params)
            fa.logEvent(name, params)
        }.onFailure {
            DebugLog.d(TAG, "Analytics log failed for $name: ${it.javaClass.simpleName}")
        }
    }

    private const val TAG = "PayVoiceAnalytics"

    // Every event name and param key lives here — no free-form strings.
    private object Event {
        const val APP_OPENED = "pv_app_opened"
        const val APP_COLD_STARTED = "pv_app_cold_started"
        const val NOTIF_PERMISSION = "pv_notif_permission"
        const val SMS_PERMISSION = "pv_sms_permission"
        const val BATTERY_EXEMPT = "pv_battery_exempt"
        const val PAIRING_STARTED = "pv_pairing_started"
        const val PAIRING_COMPLETED = "pv_pairing_completed"
        const val PAIRING_FAILED = "pv_pairing_failed"
        const val EMPLOYEE_REVOKED = "pv_employee_revoked"
        const val PAYMENT_CAPTURED_LOCAL = "pv_payment_captured_local"
        const val PAYMENT_ANNOUNCED_LOCAL = "pv_payment_announced_local"
        const val REMOTE_DELIVERY_SUCCEEDED = "pv_remote_delivery_succeeded"
        const val REMOTE_DELIVERY_FAILED = "pv_remote_delivery_failed"
        const val CROSS_CHANNEL_SUPPRESSED = "pv_cross_channel_suppressed"
        const val NON_FATAL_ERROR = "pv_non_fatal_error"
    }

    private object Param {
        const val RESULT = "result"
        const val REASON = "reason"
        const val STAGE = "stage"
        const val SOURCE = "source"
        const val AREA = "area"
        const val ERROR_CLASS = "error_class"
        const val DURATION_MS = "duration_ms"
        const val LATENCY_MS = "latency_ms"
        const val COLD_START_MS = "cold_start_ms"
    }

    private const val USER_PROP_ROLE = "pv_role"

    enum class PermissionResult { GRANTED, DENIED, PERMANENTLY_DENIED }
    enum class PairingFailure { INVALID, EXPIRED, ALREADY_USED, UNAUTHENTICATED, NETWORK }
    enum class ErrorArea { FIREBASE, SUPABASE, FCM, TTS, SMS, PIPELINE }
}

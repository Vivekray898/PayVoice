package com.vivekray898.payvoice.core.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Single debug-gated logger (reliability-work logging rule: "Only log in
 * FLAG_DEBUGGABLE builds"). Every diagnostic log should route through here
 * so release logcat stays silent no matter which call site a future change
 * adds.
 *
 * Initialized once from [com.vivekray898.payvoice.PayVoiceApp.onCreate].
 * Before initialization — and in JVM unit tests — [enabled] is false, so
 * every method is a no-op (never throws).
 *
 * Rules encoded here:
 *  - Every level is gated. Release builds emit nothing at all: a release APK
 *    that writes to logcat leaks install id, device state and payment timing
 *    to any app holding READ_LOGS on older platforms or to a connected adb
 *    host, so errors are no longer exempt.
 *  - Release additionally compiles the calls out entirely via
 *    `-assumenosideeffects` in app/proguard-rules.pro, so the string
 *    concatenation at each call site costs nothing in a release build.
 *  - Call sites must never include payment content, even in debug.
 */
object DebugLog {

    @Volatile
    private var enabled: Boolean = false

    /** Called once from PayVoiceApp.onCreate. Idempotent. */
    fun init(appContext: Context) {
        enabled = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** For classes constructed before app onCreate (defensive; keeps first init). */
    fun initFrom(context: Context) {
        if (!enabled) runCatching { init(context.applicationContext) }
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun v(tag: String, message: String) {
        if (enabled) Log.v(tag, message)
    }

    /** Operational warnings (network failures, HTTP codes) — also gated. */
    fun w(tag: String, message: String) {
        if (enabled) Log.w(tag, message)
    }

    /** Real errors. Gated like every other level: release builds stay silent. */
    fun e(tag: String, message: String, t: Throwable? = null) {
        if (!enabled) return
        if (t != null) Log.e(tag, message, t) else Log.e(tag, message)
    }
}

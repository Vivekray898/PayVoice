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
 *  - [d]/[v]/[w]: operational info (package names, event-id prefixes, stage
 *    names, latency, HTTP status) → gated, release builds never emit them.
 *  - [e]: genuine errors are intentionally NOT gated so field failures
 *    surface — but call sites must still never include payment content.
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

    /** Real errors: intentionally ungated so release failures are visible. */
    fun e(tag: String, message: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, message, t) else Log.e(tag, message)
    }
}

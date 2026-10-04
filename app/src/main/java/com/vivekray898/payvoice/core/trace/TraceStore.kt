package com.vivekray898.payvoice.core.trace

import android.content.Context
import android.content.pm.ApplicationInfo
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.database.TraceEventEntity
import com.vivekray898.payvoice.core.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room-backed [PaymentTrace.Sink].
 *
 * Writes are launched on a caller-supplied scope with `runCatching` inside, so
 * a trace failure can never propagate into the capture/announce path — the
 * whole point of tracing is that it is invisible to the pipeline it observes.
 */
class TraceStore(
    private val db: PayVoiceDatabase,
    private val scope: CoroutineScope,
) : PaymentTrace.Sink {

    override fun write(hop: PaymentTrace.Hop) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                db.traceEventDao().insert(hop.toEntity())
                db.traceEventDao().trim()
            }.onFailure { DebugLog.d(TAG, "trace write failed: ${it.javaClass.simpleName}") }
        }
    }

    override fun writeCapture(hop: PaymentTrace.Hop, meta: PaymentTrace.CaptureMeta) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                db.traceEventDao().insert(
                    hop.toEntity().copy(
                        sourcePackage = meta.packageName,
                        notificationId = meta.notificationId,
                        notificationTag = meta.tag,
                        notificationCategory = meta.category,
                        notificationFlags = meta.flags,
                        postedAtMs = meta.postedAtMs,
                        isUpdate = meta.isUpdate,
                        maskedText = meta.textShape,
                    )
                )
                db.traceEventDao().trim()
            }.onFailure { DebugLog.d(TAG, "trace capture write failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun PaymentTrace.Hop.toEntity() = TraceEventEntity(
        correlationId = correlationId,
        outcome = outcome.name,
        atMs = atMs,
        reason = reason,
        detail = detail,
    )

    companion object {
        private const val TAG = "PayVoiceTrace"

        /**
         * Installs the store **only** for a debuggable process. This is the
         * single place the gate is decided, so no caller can accidentally turn
         * tracing on in a release build. Returns true when tracing is live.
         */
        fun installIfDebuggable(
            context: Context,
            db: PayVoiceDatabase,
            scope: CoroutineScope,
        ): Boolean {
            val debuggable = (context.applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!debuggable) return false
            PaymentTrace.install(TraceStore(db, scope))
            DebugLog.d(TAG, "trace enabled (debug build)")
            return true
        }
    }
}
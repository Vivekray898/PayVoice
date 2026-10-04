package com.vivekray898.payvoice.core.trace

import android.content.Context
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.database.TraceEventEntity
import com.vivekray898.payvoice.core.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports the trace table as JSONL — one JSON object per hop, oldest first — so
 * a set of real payments taken on a phone can be attached to a bug report.
 *
 * Debug-only in practice: the UI that calls this is gated on a debuggable
 * build. The file goes to the app's own external files directory, which is
 * removed with the app and needs no storage permission.
 */
object TraceExporter {

    private const val TAG = "PayVoiceTrace"
    private const val FILE_NAME = "payvoice-trace.jsonl"

    /** Where the export was written, and how many hops it holds. */
    data class Result(val path: String, val hops: Int)

    /**
     * Writes the export. Returns null when it failed (no external storage, for
     * instance) so the caller can say so instead of implying success.
     */
    suspend fun export(db: PayVoiceDatabase, context: Context): Result? =
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = db.traceEventDao().allForExport(EXPORT_LIMIT)
                val file = File(targetDir(context), FILE_NAME)
                file.bufferedWriter().use { out ->
                    rows.forEach { row ->
                        out.write(row.toJsonLine())
                        out.newLine()
                    }
                }
                DebugLog.d(TAG, "trace exported hops=${rows.size}")
                Result(file.absolutePath, rows.size)
            }.getOrElse {
                DebugLog.e(TAG, "trace export failed", it)
                null
            }
        }

    private const val EXPORT_LIMIT = 5_000

    private fun targetDir(context: Context): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }

    /**
     * Minimal JSON escaping. Deliberately hand-rolled rather than pulling in
     * org.json / a serializer: the schema is six fields and a dependency would
     * have to be added to `verification-metadata.xml` for no gain.
     */
    private fun String.jsonEscape(): String = buildString(length + 8) {
        for (ch in this@jsonEscape) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
        }
    }

    private fun str(value: String?): String =
        if (value == null) "null" else "\"${value.jsonEscape()}\""

    private fun num(value: Int?): String = value?.toString() ?: "null"

    private fun bool(value: Boolean?): String = value?.toString() ?: "null"

    private fun TraceEventEntity.toJsonLine(): String = buildString {
        append('{')
        append("\"cid\":").append(str(correlationId)).append(',')
        append("\"hop\":").append(str(outcome)).append(',')
        append("\"atMs\":").append(atMs).append(',')
        append("\"reason\":").append(str(reason)).append(',')
        append("\"detail\":").append(str(detail))
        if (sourcePackage != null || maskedText != null) {
            // Capture metadata: only present on the CAPTURED row of a payment.
            append(",\"pkg\":").append(str(sourcePackage))
            append(",\"notifId\":").append(num(notificationId))
            append(",\"notifTag\":").append(str(notificationTag))
            append(",\"notifCategory\":").append(str(notificationCategory))
            append(",\"notifFlags\":").append(num(notificationFlags))
            append(",\"postedAtMs\":").append(postedAtMs?.toString() ?: "null")
            append(",\"isUpdate\":").append(bool(isUpdate))
            append(",\"textShape\":").append(str(maskedText))
        }
        append('}')
    }
}
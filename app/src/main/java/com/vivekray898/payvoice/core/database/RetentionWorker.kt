package com.vivekray898.payvoice.core.database

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vivekray898.payvoice.PayVoiceApp
import java.util.concurrent.TimeUnit

/**
 * Daily retention pass — the only scheduled work in the app (spec §20).
 * Everything else in PayVoice is event-driven.
 */
class RetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PayVoiceApp
        val settings = app.container.settings.settings.value
        RetentionCleaner(app.container.database).clean(
            dedupHours = settings.dedupRetentionHours,
            historyDays = settings.historyRetentionDays,
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "payvoice_retention"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

package com.vivekray898.payvoice.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vivekray898.payvoice.PayVoiceApp
import com.vivekray898.payvoice.core.database.RetentionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boot receiver (Phase 16): the smallest supported restore mechanism.
 * Re-schedules the retention job and warms the container so the first capture
 * after reboot pays no cold-init cost. The NotificationListenerService is
 * re-bound by the OS itself and needs no boot logic.
 */
class PayVoiceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as? PayVoiceApp
        if (app == null) {
            pending.finish()
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            try {
                RetentionWorker.schedule(context)
                // Touch lazy singletons off-main so the pipeline is ready.
                runCatching { app.container.database.openHelper.readableDatabase }
            } finally {
                pending.finish()
            }
        }
    }
}

package com.vivekray898.payvoice.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.vivekray898.payvoice.PayVoiceApp
import com.vivekray898.payvoice.core.model.CaptureEvent
import com.vivekray898.payvoice.core.model.CaptureSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SMS backup capture path (Phases 3, 13): reacts to newly delivered bank SMS
 * when app notifications are missing. Rules:
 *  - never long-running work inside onReceive(): goAsync() + pipeline scope,
 *    finish() as soon as the handoff completes;
 *  - the process does the parse/dedup/TTS in the background;
 *  - no permanent foreground service, no socket, no Activity dependency.
 */
class PayVoiceSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Concatenate multi-part SMS into one body; all parts share the origin.
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (sender.isBlank() || body.isBlank()) return

        val pending = goAsync()
        val app = context.applicationContext as? PayVoiceApp
        if (app == null) {
            pending.finish()
            return
        }
        val event = CaptureEvent(
            captureSource = CaptureSource.SMS_BANK, // refined by parser/bank resolution
            originId = sender,
            title = null,
            body = body,
            postedAtMs = System.currentTimeMillis(),
        )
        app.container.applicationScope.launch(Dispatchers.Default) {
            try {
                app.container.pipeline.handleCapture(event)
            } finally {
                pending.finish()
            }
        }
    }
}

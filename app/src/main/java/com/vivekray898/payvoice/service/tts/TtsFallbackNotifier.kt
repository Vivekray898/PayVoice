package com.vivekray898.payvoice.service.tts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vivekray898.payvoice.MainActivity
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.core.util.DebugLog

/**
 * Fallback notification for when TTS cannot speak an announcement.
 * Guarantees the user sees the payment even if audio fails.
 *
 * Channel importance is DEFAULT (audible) — this is a real notification
 * the user must see. Distinct channel from PaymentNotification's silent
 * FCM-priority receipt.
 */
object TtsFallbackNotifier {

    private const val CHANNEL_ID = "payvoice_tts_fallback"
    private const val CHANNEL_NAME = "Announcement failures"
    private const val CHANNEL_DESC = "Shown when a payment could not be spoken aloud"
    private const val TAG = "TtsFallback"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun notify(context: Context, announcementText: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.d(TAG, "POST_NOTIFICATIONS not granted — fallback suppressed")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val id = ("tts_fail_" + System.currentTimeMillis()).hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Payment announcement (audio failed)")
            .setContentText(announcementText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(announcementText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            DebugLog.d(TAG, "fallback notification posted")
        } catch (e: SecurityException) {
            // Permission revoked mid-flight — nothing we can do.
        }
    }
}

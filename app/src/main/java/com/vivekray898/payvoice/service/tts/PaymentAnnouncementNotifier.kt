package com.vivekray898.payvoice.service.tts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vivekray898.payvoice.MainActivity
import com.vivekray898.payvoice.R
import com.vivekray898.payvoice.core.util.DebugLog

/**
 * High-priority notification posted when a payment arrives. Its ONLY purpose
 * is to force the device to wake from Doze / screen-lock so that the TTS
 * engine can run. It is auto-cancelled after the TTS finishes.
 *
 * Channel importance is HIGH with the default notification sound so Android
 * treats it as time-sensitive and grants a wake-lock-adjacent privilege
 * (the same mechanism banking apps use for transaction alerts).
 */
object PaymentAnnouncementNotifier {

    private const val CHANNEL_ID = "payvoice_payment_announcement"
    private const val CHANNEL_NAME = "Payment announcements"
    private const val CHANNEL_DESC = "Played when a payment is received"
    private const val TAG = "PayVoiceAnnounce"
    private const val NOTIFICATION_ID = 9002   // distinct from PaymentNotification (9001)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            // Default sound — required for the wake-up privilege.
            // Do NOT set a custom Uri; let the system use the user's default.
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Post the wake-up notification. Returns true if posted successfully.
     * The caller cancels it after TTS finishes.
     *
     * @param showDetails false posts it as VISIBILITY_PRIVATE, so the lock
     *   screen and the shade show only the title. The notification still wakes
     *   the device — that is the whole point of it — and TTS still speaks the
     *   amount, so turning this off costs nothing functionally.
     */
    fun post(context: Context, announcementText: String, showDetails: Boolean = true): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            DebugLog.d(TAG, "POST_NOTIFICATIONS not granted — wake-up notification skipped")
            return false
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Payment received")
            .setContentText(announcementText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(announcementText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(
                if (showDetails) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                },
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            DebugLog.d(TAG, "wake-up notification posted")
            true
        } catch (e: SecurityException) {
            DebugLog.d(TAG, "notify threw SecurityException — permission revoked")
            false
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}

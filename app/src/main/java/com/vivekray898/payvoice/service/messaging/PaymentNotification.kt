package com.vivekray898.payvoice.service.messaging

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

/**
 * Posts a minimal, silent, auto-dismissible notification when a remote
 * payment is announced. This exists for ONE reason: FCM downgrades the
 * high-priority channel to normal priority if high-priority messages
 * don't produce a user-visible notification (Google's documented behavior).
 * The notification is the "user-visible" signal FCM requires.
 *
 * Design constraints:
 *  - MIN importance (no sound, no heads-up, no vibration) — won't disturb.
 *  - Auto-cancel on tap; also auto-cancels after TTS finishes.
 *  - Content is generic ("Payment announcement") — never the amount, never
 *    the sender, never the source. The TTS speaks the real content; the
 *    notification is only a delivery receipt for FCM's benefit.
 */
object PaymentNotification {

    private const val CHANNEL_ID = "payvoice_remote_announcements"
    private const val CHANNEL_NAME = "Payment announcements"
    private const val CHANNEL_DESC = "Silent receipt shown when a payment is announced"
    private const val NOTIFICATION_ID = 9001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_MIN,   // silent, no peek
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Posts (or replaces) the silent announcement receipt. Safe to call from
     * any thread; uses NotificationManagerCompat which handles POST_NOTIFICATIONS
     * permission checks (silently no-ops if permission is missing on API 33+).
     */
    fun post(context: Context) {
        ensureChannel(context)

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
            .setSmallIcon(R.mipmap.ic_launcher)  // uses your existing icon
            .setContentTitle("Payment announced")
            .setContentText("A payment was just announced on this device.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()

        // POST_NOTIFICATIONS is a runtime permission on API 33+. If denied, notify()
        // is a no-op and can throw SecurityException. Never let a notification
        // failure crash the caller — this is a best-effort receipt.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and notify() — swallow.
        }
    }

    /** Removes the receipt — called when TTS finishes (or after a delay). */
    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}
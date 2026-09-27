package com.vivekray898.payvoice.service.setup

import android.Manifest
import android.app.Notification
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
 * PayVoice's OWN notification channels (spec: B — distinct from Notification
 * Listener access). Channels must exist BEFORE requesting POST_NOTIFICATIONS
 * so the permission lands on real, meaningful channels.
 */
object SetupNotifications {

    /** Real payment/announcement-related events. High priority by spec. */
    const val CHANNEL_PAYMENT_EVENTS = "payment_events"

    /** Setup confirmations, device status, service notices. */
    const val CHANNEL_STATUS = "status"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val payment = NotificationChannel(
            CHANNEL_PAYMENT_EVENTS,
            "Payment events",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming payment detections and announcements"
        }

        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Device status",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Setup progress and service status"
        }

        manager.createNotificationChannels(listOf(payment, status))
    }

    fun canPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * A legitimate, visible setup notification. Proves the whole chain
     * (permission + channel) works and gives POST_NOTIFICATIONS something real
     * to justify itself with. Tapping opens the app.
     */
    fun postSetupConfirmation(context: Context): Boolean {
        ensureChannels(context)
        if (!canPostNotifications(context)) return false

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PayVoice is set up")
            .setContentText("Notifications are working. Payment announcements will appear here.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(1001, notification)
            true
        }.getOrDefault(false)
    }
}

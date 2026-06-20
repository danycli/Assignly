package com.danycli.assignmentchecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object UpdateNotifier {
    private const val CHANNEL_ID = "assignly_updates"
    private const val CHANNEL_NAME = "App updates"
    private const val NOTIFICATION_ID = 1001

    fun maybeNotify(context: Context, updateInfo: AppUpdateInfo) {
        if (!UpdateNotificationStore.shouldNotifyToday(context)) return
        if (!NotificationGate.areNotificationsEnabled(context)) return
        showUpdateNotification(context, updateInfo)
        UpdateNotificationStore.markNotifiedToday(context, updateInfo.latestVersionCode)
    }

    private fun showUpdateNotification(context: Context, updateInfo: AppUpdateInfo) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about new Assignly releases."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
        val largeIcon = NotificationGate.getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF004643.toInt())
            .setContentTitle("Update available")
            .setContentText("A newer version (${updateInfo.displayLabel}) is available.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

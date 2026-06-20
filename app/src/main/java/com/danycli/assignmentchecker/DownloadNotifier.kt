package com.danycli.assignmentchecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object DownloadNotifier {
    private const val CHANNEL_ID = "assignly_downloads"
    private const val CHANNEL_NAME = "Download status"

    fun showProgress(context: Context, downloadId: String, fileName: String, message: String = "Downloading…") {
        if (!NotificationGate.areNotificationsEnabled(context)) return
        ensureChannel(context)
        val largeIcon = NotificationGate.getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF004643.toInt())
            .setContentTitle("Downloading: $fileName")
            .setContentText(message)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(downloadId), notification)
    }

    fun showSuccess(context: Context, downloadId: String, fileName: String) {
        if (!NotificationGate.areNotificationsEnabled(context)) return
        ensureChannel(context)
        val largeIcon = NotificationGate.getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF004643.toInt())
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(downloadId), notification)
    }

    fun showFailure(context: Context, downloadId: String, fileName: String, reason: String?) {
        if (!NotificationGate.areNotificationsEnabled(context)) return
        ensureChannel(context)
        val largeIcon = NotificationGate.getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF004643.toInt())
            .setContentTitle("Download failed")
            .setContentText(reason?.ifBlank { fileName } ?: fileName)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(downloadId), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background download progress and status."
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(downloadId: String): Int = downloadId.hashCode()
}

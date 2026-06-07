package com.danycli.assignmentchecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object UploadNotifier {
    private const val CHANNEL_ID = "assignly_uploads"
    private const val CHANNEL_NAME = "Upload status"

    fun showProgress(context: Context, uploadId: String, assignmentTitle: String, message: String = "Uploading…") {
        if (!NotificationGate.areNotificationsEnabled(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Uploading: $assignmentTitle")
            .setContentText(message)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(uploadId), notification)
    }

    fun showSuccess(context: Context, uploadId: String, assignmentTitle: String) {
        if (!NotificationGate.areNotificationsEnabled(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Upload complete")
            .setContentText(assignmentTitle)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(uploadId), notification)
    }

    fun showFailure(context: Context, uploadId: String, assignmentTitle: String, reason: String?) {
        if (!NotificationGate.areNotificationsEnabled(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Upload failed")
            .setContentText(reason?.ifBlank { assignmentTitle } ?: assignmentTitle)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(uploadId), notification)
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
            description = "Background upload progress and status."
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(uploadId: String): Int = uploadId.hashCode()
}

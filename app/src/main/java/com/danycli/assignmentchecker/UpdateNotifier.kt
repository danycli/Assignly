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
import android.util.Log

object UpdateNotifier {
    private const val CHANNEL_ID = "assignly_updates"
    private const val CHANNEL_NAME = "App updates"
    private const val NOTIFICATION_ID = 1001

    fun maybeNotify(context: Context, updateInfo: AppUpdateInfo): UpdateState {
        val currentVersion = com.danycli.assignmentchecker.BuildConfig.VERSION_CODE
        if (updateInfo.latestVersionCode <= currentVersion) {
            Log.d("UpdateNotifier", "Remote version ${updateInfo.latestVersionCode} is not newer than installed version $currentVersion.")
            return UpdateState.NO_UPDATE
        }

        if (UpdateNotificationStore.hasNotified(context, updateInfo.latestVersionCode)) {
            Log.d("UpdateNotifier", "Notification deduplicated: Already notified for version ${updateInfo.latestVersionCode}")
            return UpdateState.ALREADY_NOTIFIED
        }
        
        if (!NotificationGate.areNotificationsEnabled(context)) {
            Log.w("UpdateNotifier", "NotificationPermissionDenied: Cannot show update notification for ${updateInfo.latestVersionCode}")
            return UpdateState.NOTIFICATION_UNAVAILABLE
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w("UpdateNotifier", "NotificationChannelDisabled: Cannot show update notification for ${updateInfo.latestVersionCode}")
                return UpdateState.NOTIFICATION_UNAVAILABLE
            }
        }
        
        showUpdateNotification(context, updateInfo)
        // Directive 4: SAVE lastNotifiedVersionCode ONLY AFTER SUCCESSFUL DELIVERY
        UpdateNotificationStore.markNotified(context, updateInfo.latestVersionCode)
        Log.i("UpdateNotifier", "NotificationPosted: Update notification shown for ${updateInfo.latestVersionCode}")
        return UpdateState.NOTIFICATION_POSTED
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

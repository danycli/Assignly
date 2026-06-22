package com.danycli.assignmentchecker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationGate {
    fun areNotificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun getAppIconBitmap(context: Context): android.graphics.Bitmap? {
        return runCatching {
            val size = (64 * context.resources.displayMetrics.density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            val paint = android.graphics.Paint().apply {
                color = 0xFF004643.toInt()
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
            }
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification) ?: return null
            val iconSize = (size * 0.55f).toInt()
            val margin = (size - iconSize) / 2
            
            val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(drawable).mutate()
            androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, android.graphics.Color.WHITE)
            
            wrapped.setBounds(margin, margin, margin + iconSize, margin + iconSize)
            wrapped.draw(canvas)
            bitmap
        }.getOrNull()
    }
}

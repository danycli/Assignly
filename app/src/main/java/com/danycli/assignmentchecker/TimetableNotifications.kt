package com.danycli.assignmentchecker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

const val CHANNEL_CLASSES = "assignly_classes"

object TimetableNotificationManager {

    private const val TIMETABLE_PREFS = "timetable_reminder_prefs"
    private const val KEY_SCHEDULED_NAMES = "scheduled_work_names"

    fun cancelAllClassReminders(context: Context) {
        val prefs = context.getSharedPreferences(TIMETABLE_PREFS, Context.MODE_PRIVATE)
        val oldNames = prefs.getStringSet(KEY_SCHEDULED_NAMES, emptySet()) ?: emptySet()
        oldNames.forEach { cancelAlarm(context, it.hashCode()) }
        prefs.edit().remove(KEY_SCHEDULED_NAMES).apply()
    }

    fun scheduleClassReminders(context: Context) {
        val settings = AppSettingsStore.get(context)
        val prefs = context.getSharedPreferences(TIMETABLE_PREFS, Context.MODE_PRIVATE)

        if (!settings.classNotificationsEnabled) {
            cancelAllClassReminders(context)
            return
        }

        val snapshot = TimetableCacheStore.loadSnapshot(context)
        if (snapshot == null || snapshot.lectures.isEmpty()) {
            cancelAllClassReminders(context)
            return
        }

        // Cancel old scheduled alarms first to avoid orphaned alarms
        cancelAllClassReminders(context)

        val now = System.currentTimeMillis()
        val newNames = mutableSetOf<String>()

        snapshot.lectures.forEach { lecture ->
            val triggerAt = calculateNextClassTriggerEpochMs(lecture.day, lecture.startTime) ?: return@forEach
            if (triggerAt <= now - 60_000) { // If it was supposed to trigger more than 1 min ago, skip
                return@forEach
            }
            
            // If triggerAt is in the past but very recent (e.g. within 5 mins window), 
            // schedule for immediate delivery by using now + small delay
            val finalTriggerAt = if (triggerAt <= now) now + 2000 else triggerAt
            
            val workName = classWorkName(lecture.courseCode, lecture.courseName, lecture.day, lecture.startTime)
            val requestCode = workName.hashCode()
            
            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                putExtra(ClassReminderReceiver.KEY_COURSE_CODE, lecture.courseCode)
                putExtra(ClassReminderReceiver.KEY_COURSE_NAME, lecture.courseName)
                putExtra(ClassReminderReceiver.KEY_ROOM, lecture.room)
                putExtra(ClassReminderReceiver.KEY_INSTRUCTOR, lecture.instructor)
                putExtra(ClassReminderReceiver.KEY_START_TIME, lecture.startTime)
                putExtra(ClassReminderReceiver.KEY_DAY, lecture.day)
                putExtra(ClassReminderReceiver.KEY_REQUEST_CODE, requestCode)
            }
            
            scheduleAlarm(context, finalTriggerAt, requestCode, intent)
            newNames.add(workName)
        }

        prefs.edit().putStringSet(KEY_SCHEDULED_NAMES, newNames).apply()
    }

    fun scheduleAlarm(context: Context, triggerAt: Long, requestCode: Int, intent: Intent) {
        if (!NotificationGate.canScheduleExactAlarms(context)) {
            Log.w("TimetableNotificationManager", "Exact alarms not allowed, scheduling inexactly.")
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("TimetableNotificationManager", "Failed to schedule exact alarm, falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ClassReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun classWorkName(courseCode: String, courseName: String, day: String, startTime: String): String {
        val identifier = courseCode.ifBlank { courseName }
        val cleanTime = startTime.replace(" ", "_").replace(":", "_")
        return "class_reminder_${identifier}_${day}_$cleanTime"
    }

    fun calculateNextClassTriggerEpochMs(
        dayOfWeekStr: String, 
        startTimeStr: String,
        skipCurrentWindow: Boolean = false
    ): Long? {
        return try {
            val now = LocalDateTime.now()
            val dayLower = dayOfWeekStr.trim().lowercase()
            val targetDay = when {
                dayLower.startsWith("mon") -> DayOfWeek.MONDAY
                dayLower.startsWith("tue") -> DayOfWeek.TUESDAY
                dayLower.startsWith("wed") -> DayOfWeek.WEDNESDAY
                dayLower.startsWith("thu") -> DayOfWeek.THURSDAY
                dayLower.startsWith("fri") -> DayOfWeek.FRIDAY
                dayLower.startsWith("sat") -> DayOfWeek.SATURDAY
                dayLower.startsWith("sun") -> DayOfWeek.SUNDAY
                else -> return null
            }
            val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            val time = LocalTime.parse(startTimeStr.trim().uppercase(), formatter)
            
            var targetDateTime = now.with(TemporalAdjusters.nextOrSame(targetDay)).with(time).withSecond(0).withNano(0)
            var triggerTime = targetDateTime.minusMinutes(5)
            
            // If we are already past the 5-minute reminder window for today, or if skipCurrentWindow is true
            if (triggerTime.isBefore(now) || (skipCurrentWindow && targetDay == now.dayOfWeek)) {
                // If the class hasn't started yet, and we are NOT skipping current window,
                // we should return triggerTime (which is in the past) so the caller can decide to fire it immediately.
                // BUT only if targetDay is TODAY.
                if (targetDay == now.dayOfWeek && targetDateTime.isAfter(now) && !skipCurrentWindow) {
                    // Stay with current triggerTime (even if slightly in the past)
                } else {
                    targetDateTime = targetDateTime.plusWeeks(1)
                    triggerTime = targetDateTime.minusMinutes(5)
                }
            }
            triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.e("TimetableNotificationManager", "Failed to parse day='$dayOfWeekStr' time='$startTimeStr'", e)
            null
        }
    }
}

object TimetableNotificationBuilder {
    fun buildReminder(context: Context, title: String, content: String): android.app.Notification {
        ensureClassNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeIcon = NotificationGate.getAppIconBitmap(context)
        return NotificationCompat.Builder(context, CHANNEL_CLASSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF004643.toInt())
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}

private fun ensureClassNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_CLASSES) == null) {
        val channel = NotificationChannel(
            CHANNEL_CLASSES,
            "Class Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders 5 minutes before scheduled lectures."
            enableLights(true)
            lightColor = android.graphics.Color.GREEN
        }
        manager.createNotificationChannel(channel)
    }
}

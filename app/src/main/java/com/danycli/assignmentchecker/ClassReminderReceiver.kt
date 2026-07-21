package com.danycli.assignmentchecker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ClassReminderReceiver", "onReceive triggered with action: ${intent.action}")
        val settings = AppSettingsStore.get(context)
        Log.d("ClassReminderReceiver", "settings.classNotificationsEnabled: ${settings.classNotificationsEnabled}")
        if (!settings.classNotificationsEnabled) {
            Log.d("ClassReminderReceiver", "classNotificationsEnabled is false, returning")
            return
        }
        val notifsEnabled = NotificationGate.areNotificationsEnabled(context)
        Log.d("ClassReminderReceiver", "NotificationGate.areNotificationsEnabled: $notifsEnabled")
        if (!notifsEnabled) {
            Log.d("ClassReminderReceiver", "notifications are disabled by system/gate, returning")
            return
        }

        val courseCode = intent.getStringExtra(KEY_COURSE_CODE).orEmpty()
        val courseName = intent.getStringExtra(KEY_COURSE_NAME).orEmpty()
        val room = intent.getStringExtra(KEY_ROOM).orEmpty()
        val instructor = intent.getStringExtra(KEY_INSTRUCTOR).orEmpty()
        val startTime = intent.getStringExtra(KEY_START_TIME).orEmpty()
        val day = intent.getStringExtra(KEY_DAY).orEmpty()
        val requestCode = intent.getIntExtra(KEY_REQUEST_CODE, 0)

        Log.d("ClassReminderReceiver", "Extracted lecture: $courseCode - $courseName, room=$room, time=$startTime, day=$day, code=$requestCode")

        if (courseCode.isBlank() && courseName.isBlank()) {
            Log.d("ClassReminderReceiver", "both courseCode and courseName are blank, returning")
            return
        }

        // 1. Show notification
        val label = courseCode.ifBlank { courseName }
        val title = "Class Reminder: $label"
        val details = mutableListOf<String>()
        if (courseName.isNotBlank()) details.add(courseName)
        details.add("starts in 5 mins")
        if (room.isNotBlank()) details.add("Room $room")
        if (instructor.isNotBlank()) details.add("Instructor: $instructor")

        val content = details.joinToString(" • ")

        val notification = TimetableNotificationBuilder.buildReminder(
            context = context,
            title = title,
            content = content
        )

        try {
            NotificationManagerCompat.from(context)
                .notify(label.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e("ClassReminderReceiver", "Notification permission missing", e)
        }

        // 2. Reschedule for next week (7 days later)
        // Pass skipCurrentWindow=true to ensure we move to the next occurrence
        val nextTriggerAt = TimetableNotificationManager.calculateNextClassTriggerEpochMs(day, startTime, skipCurrentWindow = true)
        if (nextTriggerAt != null) {
            val nextIntent = Intent(context, ClassReminderReceiver::class.java).apply {
                putExtras(intent)
            }
            TimetableNotificationManager.scheduleAlarm(context, nextTriggerAt, requestCode, nextIntent)
        }
    }

    companion object {
        const val KEY_COURSE_CODE = "course_code"
        const val KEY_COURSE_NAME = "course_name"
        const val KEY_ROOM = "room"
        const val KEY_INSTRUCTOR = "instructor"
        const val KEY_START_TIME = "start_time"
        const val KEY_DAY = "day"
        const val KEY_REQUEST_CODE = "request_code"
    }
}

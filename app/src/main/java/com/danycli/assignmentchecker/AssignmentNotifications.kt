package com.danycli.assignmentchecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private const val CHANNEL_NEW_POSTS = "assignly_new_posts"
private const val CHANNEL_REMINDERS = "assignly_reminders"

object AssignmentNotificationStore {
    private const val PREFS_NAME = "assignment_notification_prefs"
    private const val KEY_NOTIFIED_ASSIGNMENTS = "notified_assignment_keys"

    fun getNotifiedKeys(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_NOTIFIED_ASSIGNMENTS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()
    }

    fun markNotified(context: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getNotifiedKeys(context).apply { addAll(keys) }
        prefs.edit().putStringSet(KEY_NOTIFIED_ASSIGNMENTS, updated).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NOTIFIED_ASSIGNMENTS)
            .apply()
    }
}

object AssignmentNotificationManager {
    private val reminderHours = listOf(24, 6, 1)

    fun notifyNewAssignments(context: Context, assignments: List<Assignment>) {
        if (assignments.isEmpty() || !NotificationGate.areNotificationsEnabled(context)) return
        ensureNotificationChannels(context)

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

        val notification = if (assignments.size == 1) {
            val assignment = assignments.first()
            NotificationCompat.Builder(context, CHANNEL_NEW_POSTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setColor(0xFF004643.toInt())
                .setContentTitle("New assignment posted")
                .setContentText("${assignment.courseTitle} • ${assignment.assignmentTitle}")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        } else {
            val style = NotificationCompat.InboxStyle().also { inbox ->
                assignments.take(4).forEach { assignment ->
                    inbox.addLine("${assignment.courseTitle} • ${assignment.assignmentTitle}")
                }
                if (assignments.size > 4) {
                    inbox.addLine("+${assignments.size - 4} more")
                }
            }
            NotificationCompat.Builder(context, CHANNEL_NEW_POSTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setColor(0xFF004643.toInt())
                .setContentTitle("New assignments posted")
                .setContentText("${assignments.size} new assignments")
                .setStyle(style)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        }

        NotificationManagerCompat.from(context).notify(
            "assignly_new_assignments".hashCode(),
            notification
        )
    }

    fun scheduleDeadlineReminders(
        context: Context,
        pending: List<Assignment>,
        historical: List<Assignment> = emptyList()
    ) {
        val now = System.currentTimeMillis()
        val workManager = WorkManager.getInstance(context)

        pending.forEach { assignment ->
            if (!assignment.isOpen() || assignment.status != AssignmentStatus.PENDING) {
                reminderHours.forEach { hours ->
                    workManager.cancelUniqueWork(reminderWorkName(assignment, hours))
                }
                return@forEach
            }
            val deadlineEpoch = assignmentDeadlineEpoch(assignment.deadline) ?: run {
                reminderHours.forEach { hours ->
                    workManager.cancelUniqueWork(reminderWorkName(assignment, hours))
                }
                return@forEach
            }
            reminderHours.forEach { hours ->
                val triggerAt = deadlineEpoch - TimeUnit.HOURS.toMillis(hours.toLong())
                val workName = reminderWorkName(assignment, hours)
                if (triggerAt <= now) {
                    workManager.cancelUniqueWork(workName)
                    return@forEach
                }
                val delayMs = triggerAt - now
                val request = OneTimeWorkRequestBuilder<AssignmentReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag("assignly_reminder")
                    .setInputData(
                        workDataOf(
                            AssignmentReminderWorker.KEY_ASSIGNMENT_TITLE to assignment.assignmentTitle,
                            AssignmentReminderWorker.KEY_COURSE_TITLE to assignment.courseTitle,
                            AssignmentReminderWorker.KEY_REMINDER_HOURS to hours
                        )
                    )
                    .build()
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            }
        }

        historical.forEach { assignment ->
            reminderHours.forEach { hours ->
                workManager.cancelUniqueWork(reminderWorkName(assignment, hours))
            }
        }
    }

    fun cancelAllReminders(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("assignly_reminder")
    }

    private fun reminderWorkName(assignment: Assignment, hours: Int): String {
        return "assignly_deadline_${assignmentIdentityKey(assignment).hashCode()}_$hours"
    }
}

object AssignmentNotificationBuilder {
    fun buildReminder(context: Context, title: String, content: String): android.app.Notification {
        ensureNotificationChannels(context)
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
        return NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF004643.toInt())
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}

private fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // 1. New Posts Channel (High Priority)
    if (manager.getNotificationChannel(CHANNEL_NEW_POSTS) == null) {
        val channel = NotificationChannel(
            CHANNEL_NEW_POSTS,
            "New Post Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for newly posted assignments."
            enableLights(true)
            lightColor = android.graphics.Color.BLUE
        }
        manager.createNotificationChannel(channel)
    }

    // 2. Deadline Reminders Channel (Default Priority)
    if (manager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Deadline Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders for upcoming assignment deadlines."
        }
        manager.createNotificationChannel(channel)
    }

    // Optional: Remove old legacy channel if it exists
    if (manager.getNotificationChannel("assignly_assignments") != null) {
        manager.deleteNotificationChannel("assignly_assignments")
    }
}

fun assignmentNotificationKey(assignment: Assignment): String {
    return listOf(
        assignment.courseTitle.trim().lowercase(),
        assignment.assignmentTitle.trim().lowercase(),
        assignment.deadline.trim(),
        assignment.submitLink.trim()
    ).joinToString("|")
}

fun assignmentIdentityKey(assignment: Assignment): String {
    return listOf(
        assignment.courseTitle.trim().lowercase(),
        assignment.assignmentTitle.trim().lowercase()
    ).joinToString("|")
}

fun detectNewAssignments(
    previous: CachedAssignmentsSnapshot?,
    pending: List<Assignment>,
    historical: List<Assignment>
): List<Assignment> {
    if (previous == null) return emptyList()
    val knownKeys = (previous.pendingAssignments + previous.historicalAssignments)
        .map { assignmentNotificationKey(it) }
        .toSet()
    return pending.filter { assignmentNotificationKey(it) !in knownKeys }
}

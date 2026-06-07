package com.danycli.assignmentchecker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AssignmentReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = AppSettingsStore.get(applicationContext)
        if (!settings.assignmentNotificationsEnabled) return Result.success()
        if (!NotificationGate.areNotificationsEnabled(applicationContext)) return Result.success()

        val assignmentTitle = inputData.getString(KEY_ASSIGNMENT_TITLE).orEmpty()
        val courseTitle = inputData.getString(KEY_COURSE_TITLE).orEmpty()

        val snapshot = AssignmentCacheStore.loadSnapshot(applicationContext)
        val isStillPending = snapshot?.pendingAssignments?.any {
            it.assignmentTitle.equals(assignmentTitle, ignoreCase = true) &&
                    it.courseTitle.equals(courseTitle, ignoreCase = true)
        } ?: true

        if (!isStillPending) return Result.success()

        val hours = inputData.getInt(KEY_REMINDER_HOURS, 0)
        val title = if (hours > 0) "Assignment due in ${hours}h" else "Assignment due soon"
        val content = listOf(courseTitle, assignmentTitle).filter { it.isNotBlank() }.joinToString(" • ")
        val notification = AssignmentNotificationBuilder.buildReminder(
            context = applicationContext,
            title = title,
            content = content.ifBlank { "Open app for details." }
        )
        androidx.core.app.NotificationManagerCompat.from(applicationContext)
            .notify(id.hashCode(), notification)

        return Result.success()
    }

    companion object {
        const val KEY_ASSIGNMENT_TITLE = "assignment_title"
        const val KEY_COURSE_TITLE = "course_title"
        const val KEY_REMINDER_HOURS = "reminder_hours"
    }
}

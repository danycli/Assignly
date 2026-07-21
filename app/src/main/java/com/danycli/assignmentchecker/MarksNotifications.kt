package com.danycli.assignmentchecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

data class MarksChange(
    val courseName: String,
    val courseCode: String,
    val categoryName: String,
    val title: String,
    val previousObtained: Double?, // null if it is a new upload
    val currentObtained: Double,
    val totalMarks: Double,
    val isNew: Boolean
)

object MarksNotificationManager {
    private const val CHANNEL_MARKS = "assignly_marks"

    fun notifyMarksChanges(context: Context, changes: List<MarksChange>) {
        if (changes.isEmpty() || !NotificationGate.areNotificationsEnabled(context)) return
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101, // unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = NotificationGate.getAppIconBitmap(context)

        val notification = if (changes.size == 1) {
            val change = changes.first()
            val text = if (change.isNew) {
                "${change.categoryName} • ${change.title} uploaded: ${formatMark(change.currentObtained)}/${formatMark(change.totalMarks)}"
            } else {
                "${change.categoryName} • ${change.title} updated: ${formatMark(change.previousObtained ?: 0.0)} -> ${formatMark(change.currentObtained)}/${formatMark(change.totalMarks)}"
            }
            NotificationCompat.Builder(context, CHANNEL_MARKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setColor(0xFF004643.toInt())
                .setContentTitle(getShortCourseName(change.courseName))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        } else {
            val style = NotificationCompat.InboxStyle().also { inbox ->
                changes.take(4).forEach { change ->
                    val line = if (change.isNew) {
                        "${getShortCourseName(change.courseName)}: ${change.title} uploaded (${formatMark(change.currentObtained)}/${formatMark(change.totalMarks)})"
                    } else {
                        "${getShortCourseName(change.courseName)}: ${change.title} updated to ${formatMark(change.currentObtained)}/${formatMark(change.totalMarks)}"
                    }
                    inbox.addLine(line)
                }
                if (changes.size > 4) {
                    inbox.addLine("+${changes.size - 4} more")
                }
            }
            NotificationCompat.Builder(context, CHANNEL_MARKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setColor(0xFF004643.toInt())
                .setContentTitle("Assessment marks updated")
                .setContentText("${changes.size} marks uploaded or changed")
                .setStyle(style)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(
                "assignly_marks_changes".hashCode(),
                notification
            )
        }
    }

    private fun formatMark(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format("%.0f", value)
        } else {
            String.format("%.1f", value)
        }
    }

    private fun getShortCourseName(fullName: String): String {
        var name = fullName.trim()
        val prefixes = listOf("introduction to ", "intro to ", "fundamentals of ", "principles of ")
        for (prefix in prefixes) {
            if (name.lowercase().startsWith(prefix)) {
                name = name.substring(prefix.length).trim()
            }
        }
        val lower = name.lowercase()
        when {
            lower.contains("object oriented programming") -> return "OOP"
            lower.contains("data structures") -> return "Data Structures"
            lower.contains("database systems") || lower.contains("database management") -> return "Database Systems"
            lower.contains("digital logic") -> return "Digital Logic"
            lower.contains("calculus") -> return "Calculus"
            lower.contains("software engineering") -> return "Software Eng."
            lower.contains("computer architecture") -> return "Computer Arch."
            lower.contains("theory of automata") -> return "Automata Theory"
            lower.contains("probability and statistics") -> return "Prob & Stats"
            lower.contains("artificial intelligence") -> return "AI"
            lower.contains("machine learning") -> return "ML"
            lower.contains("computer networks") -> return "Networks"
            lower.contains("operating systems") -> return "Operating Systems"
            lower.contains("assembly language") -> return "Assembly"
            lower.contains("discrete structures") || lower.contains("discrete mathematics") -> return "Discrete Math"
            lower.contains("differential equations") -> return "Diff Equations"
            lower.contains("linear algebra") -> return "Linear Algebra"
            lower.contains("human computer interaction") -> return "HCI"
            lower.contains("web engineering") || lower.contains("web development") -> return "Web Eng."
            lower.contains("information security") || lower.contains("cyber security") -> return "Info Security"
        }
        name = name.replace("(?i)\\band\\b".toRegex(), "&")
        if (name.length > 20) {
            if (name.contains("&")) {
                val parts = name.split("&")
                if (parts[0].trim().length >= 8) {
                    name = parts[0].trim()
                }
            }
            val words = name.split(" ")
            if (words.size > 3) {
                name = words.take(3).joinToString(" ")
            }
            if (name.length > 20) {
                name = name.take(18) + ".."
            }
        }
        return name.trim()
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_MARKS) == null) {
            val channel = NotificationChannel(
                CHANNEL_MARKS,
                "Marks Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when assessment marks are uploaded or updated."
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
            }
            manager.createNotificationChannel(channel)
        }
    }
}

fun detectMarksChanges(previous: List<CourseMarks>, current: List<CourseMarks>): List<MarksChange> {
    if (previous.isEmpty()) return emptyList()
    val changes = mutableListOf<MarksChange>()
    val prevCourseMap = previous.associateBy { it.courseCode.trim().uppercase() }

    for (currCourse in current) {
        val prevCourse = prevCourseMap[currCourse.courseCode.trim().uppercase()]
        if (prevCourse == null) {
            // Course not in previous cache; populate it silently without notification spam
            continue
        }

        val prevCatMap = prevCourse.categories.associateBy { it.categoryName.trim().lowercase() }
        for (currCat in currCourse.categories) {
            val prevCat = prevCatMap[currCat.categoryName.trim().lowercase()]
            if (prevCat == null) {
                // New category added
                for (currItem in currCat.items) {
                    changes.add(
                        MarksChange(
                            courseName = currCourse.courseName,
                            courseCode = currCourse.courseCode,
                            categoryName = currCat.categoryName,
                            title = currItem.title,
                            previousObtained = null,
                            currentObtained = currItem.obtainedMarks,
                            totalMarks = currItem.totalMarks,
                            isNew = true
                        )
                    )
                }
                continue
            }

            val prevItemMap = prevCat.items.associateBy { it.title.trim().lowercase() }
            for (currItem in currCat.items) {
                val prevItem = prevItemMap[currItem.title.trim().lowercase()]
                if (prevItem == null) {
                    // New item added
                    changes.add(
                        MarksChange(
                            courseName = currCourse.courseName,
                            courseCode = currCourse.courseCode,
                            categoryName = currCat.categoryName,
                            title = currItem.title,
                            previousObtained = null,
                            currentObtained = currItem.obtainedMarks,
                            totalMarks = currItem.totalMarks,
                            isNew = true
                        )
                    )
                } else if (prevItem.obtainedMarks != currItem.obtainedMarks || prevItem.totalMarks != currItem.totalMarks) {
                    // Item updated
                    changes.add(
                        MarksChange(
                            courseName = currCourse.courseName,
                            courseCode = currCourse.courseCode,
                            categoryName = currCat.categoryName,
                            title = currItem.title,
                            previousObtained = prevItem.obtainedMarks,
                            currentObtained = currItem.obtainedMarks,
                            totalMarks = currItem.totalMarks,
                            isNew = false
                        )
                    )
                }
            }
        }
    }
    return changes
}

package com.danycli.assignmentchecker

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun isAssignmentDeadlineOpen(deadline: String, now: Date = Date()): Boolean {
    return try {
        val sdf = SimpleDateFormat("MMM dd ,yyyy HH:mm", Locale.US)
        val deadlineDate = sdf.parse(deadline)
        deadlineDate != null && now.before(deadlineDate)
    } catch (e: Exception) {
        false
    }
}

enum class AssignmentStatus {
    PENDING, NOT_SUBMITTED_CLOSED, SUBMITTED, GRADED
}

data class Assignment(
    val courseTitle: String,
    val assignmentTitle: String,
    val deadline: String,
    val downloadLink: String,
    val submitLink: String,
    val status: AssignmentStatus = AssignmentStatus.PENDING,
    val submittedDate: String? = null,
    val grade: String? = null,
    val feedback: String? = null
) {
    fun isOpen(): Boolean {
        return isAssignmentDeadlineOpen(deadline)
    }
    
    fun getOpenClosedLabel(isOpen: Boolean): String = if (isOpen) "Open" else "Closed"
}

package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val INVALID_DEADLINE_EPOCH_MS = Long.MIN_VALUE
private val deadlineFormatter = DateTimeFormatter.ofPattern("MMM dd ,yyyy HH:mm", Locale.US)
private val deadlineZoneId = ZoneId.systemDefault()
private val deadlineEpochCache = ConcurrentHashMap<String, Long>()

private fun resolveDeadlineEpoch(deadline: String): Long {
    val cached = deadlineEpochCache[deadline]
    if (cached != null) return cached

    val parsed = runCatching {
        LocalDateTime.parse(deadline, deadlineFormatter)
            .atZone(deadlineZoneId)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(INVALID_DEADLINE_EPOCH_MS)

    deadlineEpochCache.putIfAbsent(deadline, parsed)
    return deadlineEpochCache[deadline] ?: parsed
}

fun isAssignmentDeadlineOpen(deadline: String, now: Date = Date()): Boolean {
    val deadlineEpoch = resolveDeadlineEpoch(deadline)
    return deadlineEpoch != INVALID_DEADLINE_EPOCH_MS && now.time < deadlineEpoch
}

enum class AssignmentStatus {
    PENDING, NOT_SUBMITTED_CLOSED, SUBMITTED, GRADED
}

@Immutable
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

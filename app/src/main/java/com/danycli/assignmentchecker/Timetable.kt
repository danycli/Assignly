package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class TimetableLecture(
    val courseName: String,
    val courseCode: String,
    val instructor: String,
    val room: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val duration: String,
    val creditHours: String
) {
    val id: String get() = "${courseCode}_${day}_${startTime}"

    fun isCurrent(currentTime: LocalTime = LocalTime.now()): Boolean {
        val start = runCatching { LocalTime.parse(startTime, timeFormatter) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(endTime, timeFormatter) }.getOrNull() ?: return false
        return !currentTime.isBefore(start) && currentTime.isBefore(end)
    }

    fun isUpcoming(currentTime: LocalTime = LocalTime.now()): Boolean {
        val start = runCatching { LocalTime.parse(startTime, timeFormatter) }.getOrNull() ?: return false
        return currentTime.isBefore(start)
    }

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    }
}

data class TimetableDay(
    val dayName: String,
    val lectures: List<TimetableLecture>
)

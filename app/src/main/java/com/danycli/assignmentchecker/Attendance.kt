package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable

@Immutable
data class AttendanceSummary(
    val courseCode: String,
    val courseName: String,
    val totalLectures: Int,
    val present: Int,
    val absent: Int,
    val leaves: Int,
    val percentage: Double
) {
    val id: String get() = courseCode
}

@Immutable
data class AttendanceDetail(
    val date: String,
    val status: String, // "Present", "Absent", "Leave"
    val remarks: String,
    val courseCode: String
)

package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable

@Immutable
data class EnrolledCourse(
    val courseCode: String,
    val courseTitle: String,
    val creditHours: String,
    val section: String,
    val instructorName: String
)

@Immutable
data class EnrolledCoursesData(
    val courses: List<EnrolledCourse>,
    val semesterName: String
)

@Immutable
data class CourseFile(
    val title: String,
    val description: String,
    val uploadDate: String,
    val downloadLink: String
)


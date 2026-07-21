package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable

@Immutable
data class MarkItem(
    val title: String,
    val date: String,
    val totalMarks: Double,
    val obtainedMarks: Double,
    val percentage: String
)

@Immutable
data class MarksCategory(
    val categoryName: String,
    val items: List<MarkItem>,
    val totalMax: Double,
    val totalObtained: Double,
    val averagePct: Double
)

@Immutable
data class CourseMarks(
    val courseCode: String,
    val courseName: String,
    val categories: List<MarksCategory>
)

package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable

@Immutable
data class CourseGrade(
    val courseCode: String,
    val courseName: String,
    val creditHours: Double,
    val grade: String,
    val gradePoints: Double,
    val marks: String? = null
)

@Immutable
data class SemesterGrades(
    val semesterName: String,
    val sgpa: Double,
    val cgpa: Double,
    val creditHours: Double,
    val courses: List<CourseGrade>
)

@Immutable
data class GpaSummary(
    val cgpa: Double,
    val totalCreditHours: Double,
    val semesters: List<SemesterGrades>,
    val academicStanding: String? = null
)

fun calculateSemesterGpa(semester: SemesterGrades): Double {
    var totalQualityPoints = 0.0
    var totalCredits = 0.0
    
    for (course in semester.courses) {
        val grade = course.grade.trim().uppercase()
        val gp = course.gradePoints
        val credit = course.creditHours
        
        var isExcluded = false
        if (grade == "W" || grade == "I" || grade == "NC" || grade == "NCR" || grade.isEmpty() || grade == "-" || grade.contains("PENDING")) {
            isExcluded = true
        }
        
        if (!isExcluded && credit > 0.0) {
            totalQualityPoints += credit * gp
            totalCredits += credit
        }
    }
    
    return if (totalCredits > 0.0) totalQualityPoints / totalCredits else -1.0
}

fun hasPublishedGrades(semester: SemesterGrades): Boolean {
    for (course in semester.courses) {
        val g = course.grade.trim().lowercase()
        val m = course.marks?.trim()?.lowercase() ?: ""
        
        var marksEmptyOrZero = true
        if (m.isNotEmpty() && m != "0" && m != "0.0" && m != "null" && !m.contains("pending")) {
            marksEmptyOrZero = false
        }
        
        var gradeEmptyOrDash = true
        if (g.isNotEmpty() && g != "-" && g != "n/a" && g != "null" && !g.contains("pending")) {
            gradeEmptyOrDash = false
        }
        
        if (!marksEmptyOrZero || !gradeEmptyOrDash) {
            return true
        }
    }
    return false
}

fun isSemesterActiveOrIncomplete(
    semester: SemesterGrades,
    index: Int,
    totalSemesters: Int
): Boolean {
    if (semester.sgpa <= 0.0) {
        return true
    }
    
    val calculatedGpa = calculateSemesterGpa(semester)
    if (calculatedGpa <= 0.0) {
        return true
    }
    
    var hasPending = false
    var courseRowsCount = 0
    
    for (course in semester.courses) {
        courseRowsCount++
        val g = course.grade.trim().lowercase()
        val m = course.marks?.trim()?.lowercase() ?: ""
        
        if (g.isEmpty() || g == "-" || g == "n/a" || g == "0" || g == "0.0" || g == "null" || g.contains("pending")) {
            hasPending = true
        }
        if (m.isEmpty() || m == "-" || m == "n/a" || m == "0" || m == "0.0" || m == "null" || m.contains("pending")) {
            hasPending = true
        }
    }
    
    if (hasPending && (index == totalSemesters - 1 || courseRowsCount == 0)) {
        return true
    }
    
    return false
}

fun calculateTotalEarnedCredits(semesters: List<SemesterGrades>): Double {
    var total = 0.0
    val passedCourseCodes = mutableSetOf<String>()
    val courseCreditsMap = mutableMapOf<String, Double>()

    for (sem in semesters) {
        for (course in sem.courses) {
            val codeVal = course.courseCode.trim().uppercase()
            val cleanCode = codeVal.replace("\\s+".toRegex(), "")
            val credit = course.creditHours
            
            var passed = true
            val joined = "${course.courseCode} ${course.courseName} ${course.grade}".uppercase()
            if (joined.contains("NON CREDIT") || joined.contains("NON-CREDIT") || joined.contains("NCR") || course.grade.trim().uppercase() == "NC") {
                passed = false
            }
            
            val g = course.grade.trim().uppercase()
            if (g == "F" || g == "W" || g == "I" || g == "FA" || g == "NC" || g == "NCR" || g.isEmpty() || g == "-" || g.contains("PENDING")) {
                passed = false
            }

            if (cleanCode.isNotEmpty()) {
                courseCreditsMap[cleanCode] = credit
                if (passed) {
                    passedCourseCodes.add(cleanCode)
                }
            } else {
                if (passed) {
                    total += credit
                }
            }
        }
    }

    for (cleanCode in passedCourseCodes) {
        val credits = courseCreditsMap[cleanCode]
        if (credits != null) {
            total += credits
        }
    }

    return total
}

fun getOverallCgpa(semesters: List<SemesterGrades>): Double {
    var currentCgpa = -1.0
    for (sem in semesters) {
        if (sem.cgpa > 0.0 && sem.cgpa <= 4.0) {
            currentCgpa = sem.cgpa
        }
    }
    return if (currentCgpa > 0.0) currentCgpa else 0.0
}

fun countCompletedSemesters(semesters: List<SemesterGrades>): Int {
    var completed = 0
    for (i in semesters.indices) {
        if (!isSemesterActiveOrIncomplete(semesters[i], i, semesters.size)) {
            completed++
        }
    }
    if (completed == 0 && semesters.isNotEmpty()) {
        return maxOf(1, semesters.size - 1)
    }
    return completed
}


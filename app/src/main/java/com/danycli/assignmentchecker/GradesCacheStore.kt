package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GradesSnapshot(
    val summary: GpaSummary,
    val cachedAtEpochMs: Long
)

object GradesCacheStore {
    private const val PREFS_NAME = "secure_grades_cache"
    private const val KEY_SNAPSHOT_JSON = "grades_json"

    fun saveSnapshot(context: Context, summary: GpaSummary) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("cgpa", summary.cgpa)
            put("totalCreditHours", summary.totalCreditHours)
            put("academicStanding", summary.academicStanding)
            put("semesters", semestersToJsonArray(summary.semesters))
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): GradesSnapshot? {
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            val semesters = json.optJSONArray("semesters").toSemesterList()
            GradesSnapshot(
                summary = GpaSummary(
                    cgpa = json.optDouble("cgpa", 0.0),
                    totalCreditHours = json.optDouble("totalCreditHours", 0.0),
                    semesters = semesters,
                    academicStanding = json.optString("academicStanding").takeIf { it.isNotEmpty() && it != "null" }
                ),
                cachedAtEpochMs = json.optLong("cachedAtEpochMs", 0L)
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
    }

    private fun semestersToJsonArray(semesters: List<SemesterGrades>): JSONArray {
        return JSONArray().apply {
            semesters.forEach { item ->
                put(JSONObject().apply {
                    put("semesterName", item.semesterName)
                    put("sgpa", item.sgpa)
                    put("cgpa", item.cgpa)
                    put("creditHours", item.creditHours)
                    put("courses", coursesToJsonArray(item.courses))
                })
            }
        }
    }

    private fun coursesToJsonArray(courses: List<CourseGrade>): JSONArray {
        return JSONArray().apply {
            courses.forEach { item ->
                put(JSONObject().apply {
                    put("courseCode", item.courseCode)
                    put("courseName", item.courseName)
                    put("creditHours", item.creditHours)
                    put("grade", item.grade)
                    put("gradePoints", item.gradePoints)
                    put("marks", item.marks)
                })
            }
        }
    }

    private fun JSONArray?.toSemesterList(): List<SemesterGrades> {
        if (this == null) return emptyList()
        val result = mutableListOf<SemesterGrades>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val courses = item.optJSONArray("courses").toCourseList()
            result.add(
                SemesterGrades(
                    semesterName = item.optString("semesterName"),
                    sgpa = item.optDouble("sgpa", 0.0),
                    cgpa = item.optDouble("cgpa", 0.0),
                    creditHours = item.optDouble("creditHours", 0.0),
                    courses = courses
                )
            )
        }
        return result
    }

    private fun JSONArray?.toCourseList(): List<CourseGrade> {
        if (this == null) return emptyList()
        val result = mutableListOf<CourseGrade>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                CourseGrade(
                    courseCode = item.optString("courseCode"),
                    courseName = item.optString("courseName"),
                    creditHours = item.optDouble("creditHours", 0.0),
                    grade = item.optString("grade"),
                    gradePoints = item.optDouble("gradePoints", 0.0),
                    marks = item.optString("marks").takeIf { it.isNotEmpty() && it != "null" }
                )
            )
        }
        return result
    }
}

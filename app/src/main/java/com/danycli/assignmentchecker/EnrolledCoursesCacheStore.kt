package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EnrolledCoursesSnapshot(
    val courses: List<EnrolledCourse>,
    val semesterName: String,
    val cachedAtEpochMs: Long
)

object EnrolledCoursesCacheStore {
    private const val PREFS_NAME = "secure_enrolled_courses_cache"
    private const val KEY_SNAPSHOT_JSON = "enrolled_courses_json"

    fun saveSnapshot(context: Context, data: EnrolledCoursesData) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("semesterName", data.semesterName)
            put("courses", coursesToJsonArray(data.courses))
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): EnrolledCoursesSnapshot? {
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            val courses = json.optJSONArray("courses").toCourses()
            EnrolledCoursesSnapshot(
                courses = courses,
                semesterName = json.optString("semesterName", ""),
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

    private fun coursesToJsonArray(courses: List<EnrolledCourse>): JSONArray {
        return JSONArray().apply {
            courses.forEach { course ->
                put(JSONObject().apply {
                    put("courseCode", course.courseCode)
                    put("courseTitle", course.courseTitle)
                    put("creditHours", course.creditHours)
                    put("section", course.section)
                    put("instructorName", course.instructorName)
                })
            }
        }
    }

    private fun JSONArray?.toCourses(): List<EnrolledCourse> {
        if (this == null) return emptyList()
        val result = mutableListOf<EnrolledCourse>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                EnrolledCourse(
                    courseCode = item.optString("courseCode"),
                    courseTitle = item.optString("courseTitle"),
                    creditHours = item.optString("creditHours"),
                    section = item.optString("section"),
                    instructorName = item.optString("instructorName")
                )
            )
        }
        return result
    }
}

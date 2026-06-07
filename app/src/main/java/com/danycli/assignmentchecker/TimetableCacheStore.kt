package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TimetableSnapshot(
    val lectures: List<TimetableLecture>,
    val cachedAtEpochMs: Long
)

object TimetableCacheStore {
    private const val PREFS_NAME = "secure_timetable_cache"
    private const val KEY_SNAPSHOT_JSON = "timetable_json"

    fun saveSnapshot(context: Context, lectures: List<TimetableLecture>) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("lectures", lecturesToJsonArray(lectures))
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): TimetableSnapshot? {
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            val lectures = json.optJSONArray("lectures").toLectures()
            TimetableSnapshot(
                lectures = lectures,
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

    private fun lecturesToJsonArray(lectures: List<TimetableLecture>): JSONArray {
        return JSONArray().apply {
            lectures.forEach { lecture ->
                put(JSONObject().apply {
                    put("courseName", lecture.courseName)
                    put("courseCode", lecture.courseCode)
                    put("instructor", lecture.instructor)
                    put("room", lecture.room)
                    put("day", lecture.day)
                    put("startTime", lecture.startTime)
                    put("endTime", lecture.endTime)
                    put("duration", lecture.duration)
                    put("creditHours", lecture.creditHours)
                })
            }
        }
    }

    private fun JSONArray?.toLectures(): List<TimetableLecture> {
        if (this == null) return emptyList()
        val result = mutableListOf<TimetableLecture>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                TimetableLecture(
                    courseName = item.optString("courseName"),
                    courseCode = item.optString("courseCode"),
                    instructor = item.optString("instructor"),
                    room = item.optString("room"),
                    day = item.optString("day"),
                    startTime = item.optString("startTime"),
                    endTime = item.optString("endTime"),
                    duration = item.optString("duration"),
                    creditHours = item.optString("creditHours")
                )
            )
        }
        return result
    }
}

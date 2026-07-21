package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AttendanceSnapshot(
    val summary: List<AttendanceSummary>,
    val details: List<AttendanceDetail>,
    val cachedAtEpochMs: Long
)

object AttendanceCacheStore {
    private const val PREFS_NAME = "secure_attendance_cache"
    private const val KEY_SNAPSHOT_JSON = "attendance_json"

    fun saveSnapshot(
        context: Context,
        summary: List<AttendanceSummary>,
        details: List<AttendanceDetail>
    ) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("summary", summaryToJsonArray(summary))
            put("details", detailsToJsonArray(details))
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): AttendanceSnapshot? {
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            val summary = json.optJSONArray("summary").toSummaryList()
            val details = json.optJSONArray("details").toDetailsList()
            AttendanceSnapshot(
                summary = summary,
                details = details,
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

    private fun summaryToJsonArray(summary: List<AttendanceSummary>): JSONArray {
        return JSONArray().apply {
            summary.forEach { item ->
                put(JSONObject().apply {
                    put("courseCode", item.courseCode)
                    put("courseName", item.courseName)
                    put("totalLectures", item.totalLectures)
                    put("present", item.present)
                    put("absent", item.absent)
                    put("leaves", item.leaves)
                    put("percentage", item.percentage)
                })
            }
        }
    }

    private fun detailsToJsonArray(details: List<AttendanceDetail>): JSONArray {
        return JSONArray().apply {
            details.forEach { item ->
                put(JSONObject().apply {
                    put("date", item.date)
                    put("status", item.status)
                    put("remarks", item.remarks)
                    put("courseCode", item.courseCode)
                })
            }
        }
    }

    private fun JSONArray?.toSummaryList(): List<AttendanceSummary> {
        if (this == null) return emptyList()
        val result = mutableListOf<AttendanceSummary>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                AttendanceSummary(
                    courseCode = item.optString("courseCode"),
                    courseName = item.optString("courseName"),
                    totalLectures = item.optInt("totalLectures"),
                    present = item.optInt("present"),
                    absent = item.optInt("absent"),
                    leaves = item.optInt("leaves"),
                    percentage = item.optDouble("percentage")
                )
            )
        }
        return result
    }

    private fun JSONArray?.toDetailsList(): List<AttendanceDetail> {
        if (this == null) return emptyList()
        val result = mutableListOf<AttendanceDetail>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                AttendanceDetail(
                    date = item.optString("date"),
                    status = item.optString("status"),
                    remarks = item.optString("remarks"),
                    courseCode = item.optString("courseCode")
                )
            )
        }
        return result
    }
}

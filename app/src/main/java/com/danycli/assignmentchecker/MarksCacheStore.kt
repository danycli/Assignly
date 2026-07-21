package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MarksSnapshot(
    val courseMarksList: List<CourseMarks>,
    val cachedAtEpochMs: Long
)

object MarksCacheStore {
    private const val PREFS_NAME = "secure_marks_cache"
    private const val KEY_SNAPSHOT_JSON = "marks_json"

    fun saveSnapshot(context: Context, courseMarksList: List<CourseMarks>) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("courseMarksList", courseMarksListToJsonArray(courseMarksList))
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): MarksSnapshot? {
        val securePrefs = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
        val raw = securePrefs.getString(KEY_SNAPSHOT_JSON, null) ?: return null

        // One-time cache invalidation for the new dynamic categories parser migration (June 17, 2026)
        val migrated = securePrefs.getBoolean("migration_dynamic_marks_v1", false)
        if (!migrated) {
            clear(context)
            securePrefs.edit().putBoolean("migration_dynamic_marks_v1", true).apply()
            return null
        }

        return runCatching {
            val json = JSONObject(raw)
            val cachedAt = json.optLong("cachedAtEpochMs", 0L)
            val courseMarksList = json.optJSONArray("courseMarksList").toCourseMarksList()
            MarksSnapshot(
                courseMarksList = courseMarksList,
                cachedAtEpochMs = cachedAt
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
    }

    private fun courseMarksListToJsonArray(list: List<CourseMarks>): JSONArray {
        return JSONArray().apply {
            list.forEach { item ->
                put(JSONObject().apply {
                    put("courseCode", item.courseCode)
                    put("courseName", item.courseName)
                    put("categories", categoriesToJsonArray(item.categories))
                })
            }
        }
    }

    private fun categoriesToJsonArray(categories: List<MarksCategory>): JSONArray {
        return JSONArray().apply {
            categories.forEach { item ->
                put(JSONObject().apply {
                    put("categoryName", item.categoryName)
                    put("totalMax", item.totalMax)
                    put("totalObtained", item.totalObtained)
                    put("averagePct", item.averagePct)
                    put("items", itemsToJsonArray(item.items))
                })
            }
        }
    }

    private fun itemsToJsonArray(items: List<MarkItem>): JSONArray {
        return JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("title", item.title)
                    put("date", item.date)
                    put("totalMarks", item.totalMarks)
                    put("obtainedMarks", item.obtainedMarks)
                    put("percentage", item.percentage)
                })
            }
        }
    }

    private fun JSONArray?.toCourseMarksList(): List<CourseMarks> {
        if (this == null) return emptyList()
        val result = mutableListOf<CourseMarks>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val categories = item.optJSONArray("categories").toCategoriesList()
            result.add(
                CourseMarks(
                    courseCode = item.optString("courseCode"),
                    courseName = item.optString("courseName"),
                    categories = categories
                )
            )
        }
        return result
    }

    private fun JSONArray?.toCategoriesList(): List<MarksCategory> {
        if (this == null) return emptyList()
        val result = mutableListOf<MarksCategory>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val items = item.optJSONArray("items").toItemsList()
            result.add(
                MarksCategory(
                    categoryName = item.optString("categoryName"),
                    totalMax = item.optDouble("totalMax", 0.0),
                    totalObtained = item.optDouble("totalObtained", 0.0),
                    averagePct = item.optDouble("averagePct", 0.0),
                    items = items
                )
            )
        }
        return result
    }

    private fun JSONArray?.toItemsList(): List<MarkItem> {
        if (this == null) return emptyList()
        val result = mutableListOf<MarkItem>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                MarkItem(
                    title = item.optString("title"),
                    date = item.optString("date"),
                    totalMarks = item.optDouble("totalMarks", 0.0),
                    obtainedMarks = item.optDouble("obtainedMarks", 0.0),
                    percentage = item.optString("percentage")
                )
            )
        }
        return result
    }
}

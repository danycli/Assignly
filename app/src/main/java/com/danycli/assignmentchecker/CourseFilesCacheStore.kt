package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CourseFilesCacheStore {
    private const val PREFS_NAME = "secure_course_files_cache"

    fun saveSnapshot(context: Context, courseCode: String, files: List<CourseFile>) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("files", filesToJsonArray(files))
        }

        val cleanKey = getCleanKey(courseCode)
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(cleanKey, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context, courseCode: String): List<CourseFile> {
        val cleanKey = getCleanKey(courseCode)
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(cleanKey, null) ?: return emptyList()

        return runCatching {
            val json = JSONObject(raw)
            json.optJSONArray("files").toCourseFiles()
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .clear()
            .apply()
    }

    private fun getCleanKey(courseCode: String): String {
        return courseCode.trim().uppercase().replace("\\s+|-|•|–".toRegex(), "")
    }

    private fun filesToJsonArray(files: List<CourseFile>): JSONArray {
        return JSONArray().apply {
            files.forEach { file ->
                put(JSONObject().apply {
                    put("title", file.title)
                    put("description", file.description)
                    put("uploadDate", file.uploadDate)
                    put("downloadLink", file.downloadLink)
                })
            }
        }
    }

    private fun JSONArray?.toCourseFiles(): List<CourseFile> {
        if (this == null) return emptyList()
        val result = mutableListOf<CourseFile>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                CourseFile(
                    title = item.optString("title"),
                    description = item.optString("description"),
                    uploadDate = item.optString("uploadDate"),
                    downloadLink = item.optString("downloadLink")
                )
            )
        }
        return result
    }
}

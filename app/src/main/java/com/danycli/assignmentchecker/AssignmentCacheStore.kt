package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CachedAssignmentsSnapshot(
    val pendingAssignments: List<Assignment>,
    val historicalAssignments: List<Assignment>,
    val studentName: String?,
    val cachedAtEpochMs: Long
)

object AssignmentCacheStore {
    private const val PREFS_NAME = "secure_assignment_cache"
    private const val KEY_SNAPSHOT_JSON = "snapshot_json"
    private const val LEGACY_PREFS_NAME = "assignment_cache_prefs"

    fun saveSnapshot(
        context: Context,
        pendingAssignments: List<Assignment>,
        historicalAssignments: List<Assignment>,
        studentName: String?
    ) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", System.currentTimeMillis())
            put("studentName", studentName ?: JSONObject.NULL)
            put("pendingAssignments", assignmentsToJsonArray(pendingAssignments))
            put("historicalAssignments", assignmentsToJsonArray(historicalAssignments))
        }
        
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
            
        // Once saved to secure, clear legacy
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
    }

    fun loadSnapshot(context: Context): CachedAssignmentsSnapshot? {
        // Try secure first
        var raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null)
        
        if (raw == null) {
            // Fallback to legacy for one-time migration
            raw = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT_JSON, null)
        }

        if (raw == null) return null

        return runCatching {
            val json = JSONObject(raw)
            val pending = json.optJSONArray("pendingAssignments").toAssignments()
            val historical = json.optJSONArray("historicalAssignments").toAssignments()
            CachedAssignmentsSnapshot(
                pendingAssignments = pending,
                historicalAssignments = historical,
                studentName = json.optString("studentName").ifBlank { null },
                cachedAtEpochMs = json.optLong("cachedAtEpochMs", 0L)
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
            
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
    }

    private fun assignmentsToJsonArray(assignments: List<Assignment>): JSONArray {
        return JSONArray().apply {
            assignments.forEach { assignment ->
                put(
                    JSONObject().apply {
                        put("courseTitle", assignment.courseTitle)
                        put("assignmentTitle", assignment.assignmentTitle)
                        put("deadline", assignment.deadline)
                        put("downloadLink", assignment.downloadLink)
                        put("submitLink", assignment.submitLink)
                        put("status", assignment.status.name)
                        put("submittedDate", assignment.submittedDate ?: JSONObject.NULL)
                        put("grade", assignment.grade ?: JSONObject.NULL)
                        put("feedback", assignment.feedback ?: JSONObject.NULL)
                    }
                )
            }
        }
    }

    private fun JSONArray?.toAssignments(): List<Assignment> {
        if (this == null) return emptyList()
        val result = ArrayList<Assignment>(length())
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val status = item.optString("status")
                .let { runCatching { AssignmentStatus.valueOf(it) }.getOrDefault(AssignmentStatus.PENDING) }
            result += Assignment(
                courseTitle = item.optString("courseTitle"),
                assignmentTitle = item.optString("assignmentTitle"),
                deadline = item.optString("deadline"),
                downloadLink = item.optString("downloadLink"),
                submitLink = item.optString("submitLink"),
                status = status,
                submittedDate = item.optString("submittedDate").ifBlank { null },
                grade = item.optString("grade").ifBlank { null },
                feedback = item.optString("feedback").ifBlank { null }
            )
        }
        return result
    }
}

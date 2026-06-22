package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class UploadQueueStatus {
    QUEUED, RUNNING, SUCCESS, FAILED_RETRY, FAILED
}

data class QueuedUpload(
    val id: String,
    val assignmentTitle: String,
    val fileUri: String,
    val submitPageUrl: String,
    val status: UploadQueueStatus,
    val lastError: String?,
    val createdAtEpochMs: Long
)

object UploadQueueStore {
    private const val PREFS_NAME = "assignly_upload_queue"
    private const val KEY_UPLOADS = "uploads_json"

    fun getAll(context: Context): List<QueuedUpload> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UPLOADS, null)
            ?: return emptyList()
        return runCatching {
            val parsed = JSONArray(raw)
            buildList(parsed.length()) {
                for (i in 0 until parsed.length()) {
                    val obj = parsed.optJSONObject(i) ?: continue
                    add(obj.toQueuedUpload())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(context: Context, upload: QueuedUpload) {
        val updated = getAll(context).toMutableList()
        val existingIndex = updated.indexOfFirst { it.id == upload.id }
        if (existingIndex >= 0) {
            updated[existingIndex] = upload
        } else {
            updated.add(upload)
        }
        saveAll(context, updated)
    }

    fun updateStatus(context: Context, id: String, status: UploadQueueStatus, lastError: String? = null) {
        val updated = getAll(context).map { upload ->
            if (upload.id == id) upload.copy(status = status, lastError = lastError) else upload
        }
        saveAll(context, updated)
    }

    fun remove(context: Context, id: String) {
        val updated = getAll(context).filterNot { it.id == id }
        saveAll(context, updated)
    }

    fun clearFinished(context: Context) {
        val remaining = getAll(context).filterNot {
            it.status == UploadQueueStatus.SUCCESS || it.status == UploadQueueStatus.FAILED
        }
        saveAll(context, remaining)
    }

    private fun saveAll(context: Context, uploads: List<QueuedUpload>) {
        val json = JSONArray().apply {
            uploads.forEach { upload ->
                put(
                    JSONObject().apply {
                        put("id", upload.id)
                        put("assignmentTitle", upload.assignmentTitle)
                        put("fileUri", upload.fileUri)
                        put("submitPageUrl", upload.submitPageUrl)
                        put("status", upload.status.name)
                        put("lastError", upload.lastError ?: JSONObject.NULL)
                        put("createdAtEpochMs", upload.createdAtEpochMs)
                    }
                )
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UPLOADS, json.toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun JSONObject.toQueuedUpload(): QueuedUpload {
        return QueuedUpload(
            id = optString("id"),
            assignmentTitle = optString("assignmentTitle"),
            fileUri = optString("fileUri"),
            submitPageUrl = optString("submitPageUrl"),
            status = runCatching { UploadQueueStatus.valueOf(optString("status")) }.getOrDefault(UploadQueueStatus.QUEUED),
            lastError = optString("lastError").ifBlank { null }.takeUnless { it == "null" },
            createdAtEpochMs = optLong("createdAtEpochMs", 0L)
        )
    }
}

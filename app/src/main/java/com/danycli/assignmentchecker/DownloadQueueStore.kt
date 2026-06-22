package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadQueueStatus {
    QUEUED, RUNNING, SUCCESS, FAILED
}

data class QueuedDownload(
    val id: String,
    val fileName: String,
    val downloadLink: String,
    val status: DownloadQueueStatus,
    val lastError: String?,
    val createdAtEpochMs: Long,
    val fileUri: String? = null
)

object DownloadQueueStore {
    private const val PREFS_NAME = "assignly_download_queue"
    private const val KEY_DOWNLOADS = "downloads_json"

    fun getAll(context: Context): List<QueuedDownload> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADS, null)
            ?: return emptyList()
        return runCatching {
            val parsed = JSONArray(raw)
            buildList(parsed.length()) {
                for (i in 0 until parsed.length()) {
                    val obj = parsed.optJSONObject(i) ?: continue
                    add(obj.toQueuedDownload())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(context: Context, download: QueuedDownload) {
        val updated = getAll(context).toMutableList()
        val existingIndex = updated.indexOfFirst { it.id == download.id }
        if (existingIndex >= 0) {
            updated[existingIndex] = download
        } else {
            updated.add(download)
        }
        saveAll(context, updated)
    }

    fun updateStatus(context: Context, id: String, status: DownloadQueueStatus, lastError: String? = null, fileUri: String? = null) {
        val updated = getAll(context).map { download ->
            if (download.id == id) {
                download.copy(
                    status = status,
                    lastError = lastError,
                    fileUri = fileUri ?: download.fileUri
                )
            } else {
                download
            }
        }
        saveAll(context, updated)
    }

    fun remove(context: Context, id: String) {
        val updated = getAll(context).filterNot { it.id == id }
        saveAll(context, updated)
    }

    fun clearFinished(context: Context) {
        val remaining = getAll(context).filterNot {
            it.status == DownloadQueueStatus.SUCCESS || it.status == DownloadQueueStatus.FAILED
        }
        saveAll(context, remaining)
    }

    private fun saveAll(context: Context, downloads: List<QueuedDownload>) {
        val json = JSONArray().apply {
            downloads.forEach { download ->
                put(
                    JSONObject().apply {
                        put("id", download.id)
                        put("fileName", download.fileName)
                        put("downloadLink", download.downloadLink)
                        put("status", download.status.name)
                        put("lastError", download.lastError ?: JSONObject.NULL)
                        put("createdAtEpochMs", download.createdAtEpochMs)
                        put("fileUri", download.fileUri ?: JSONObject.NULL)
                    }
                )
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOADS, json.toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun JSONObject.toQueuedDownload(): QueuedDownload {
        return QueuedDownload(
            id = optString("id"),
            fileName = optString("fileName"),
            downloadLink = optString("downloadLink"),
            status = runCatching { DownloadQueueStatus.valueOf(optString("status")) }.getOrDefault(DownloadQueueStatus.QUEUED),
            lastError = optString("lastError").ifBlank { null }.takeUnless { it == "null" },
            createdAtEpochMs = optLong("createdAtEpochMs", 0L),
            fileUri = optString("fileUri").ifBlank { null }.takeUnless { it == "null" }
        )
    }
}

package com.danycli.assignmentchecker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID).orEmpty()
        val downloadLink = inputData.getString(KEY_DOWNLOAD_LINK).orEmpty()
        val fileName = inputData.getString(KEY_FILE_NAME).orEmpty()

        if (downloadId.isBlank() || downloadLink.isBlank()) {
            return Result.failure()
        }

        DownloadQueueStore.updateStatus(applicationContext, downloadId, DownloadQueueStatus.RUNNING)
        DownloadNotifier.showProgress(applicationContext, downloadId, fileName)

        val repository = PortalRepository()
        val credentials = CredentialsStore.get(applicationContext)
            ?: return failDownload(downloadId, fileName, "Missing saved credentials.")

        val (username, password) = credentials

        return try {
            when (repository.login(username, password)) {
                is LoginResult.Success -> {
                    when (val downloadResult = repository.downloadAssignment(downloadLink)) {
                        is DownloadResult.Success -> {
                            val savedUri = writeBytesToDownloads(applicationContext, downloadResult.fileName, downloadResult.bytes)
                            if (savedUri != null) {
                                DownloadQueueStore.updateStatus(applicationContext, downloadId, DownloadQueueStatus.SUCCESS, fileUri = savedUri)
                                DownloadNotifier.showSuccess(applicationContext, downloadId, fileName)
                                Result.success()
                            } else {
                                failDownload(downloadId, fileName, "Could not save file to Downloads.")
                            }
                        }
                        is DownloadResult.NetworkError -> retryDownload(downloadId, fileName, "Network error during download.")
                        is DownloadResult.Rejected -> failDownload(downloadId, fileName, downloadResult.reason)
                        is DownloadResult.Error -> failDownload(downloadId, fileName, downloadResult.message)
                    }
                }
                is LoginResult.InvalidCredentials -> failDownload(downloadId, fileName, "Session expired. Please log in again.")
                is LoginResult.CaptchaRequired -> failDownload(downloadId, fileName, "Security verification required in app.")
                is LoginResult.Error -> retryDownload(downloadId, fileName, "Login failed in background download.")
            }
        } catch (io: IOException) {
            retryDownload(downloadId, fileName, "Network error.")
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed: ${e.message}", e)
            failDownload(downloadId, fileName, e.message ?: "Unexpected download error.")
        }
    }

    private fun retryDownload(downloadId: String, fileName: String, reason: String): Result {
        DownloadQueueStore.updateStatus(applicationContext, downloadId, DownloadQueueStatus.QUEUED, reason)
        DownloadNotifier.showProgress(applicationContext, downloadId, fileName, "Retrying soon…")
        return Result.retry()
    }

    private fun failDownload(downloadId: String, fileName: String, reason: String): Result {
        DownloadQueueStore.updateStatus(applicationContext, downloadId, DownloadQueueStatus.FAILED, reason)
        DownloadNotifier.showFailure(applicationContext, downloadId, fileName, reason)
        return Result.failure(
            Data.Builder()
                .putString(KEY_ERROR_MESSAGE, reason)
                .build()
        )
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_LINK = "download_link"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}

object DownloadWorkScheduler {
    fun enqueue(
        context: Context,
        fileName: String,
        downloadLink: String
    ): String {
        val downloadId = UUID.randomUUID().toString()
        val appContext = context.applicationContext
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            DownloadQueueStore.upsert(
                context = appContext,
                download = QueuedDownload(
                    id = downloadId,
                    fileName = fileName,
                    downloadLink = downloadLink,
                    status = DownloadQueueStatus.QUEUED,
                    lastError = null,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                        .putString(DownloadWorker.KEY_DOWNLOAD_LINK, downloadLink)
                        .putString(DownloadWorker.KEY_FILE_NAME, fileName)
                        .build()
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(appContext).enqueue(request)
        }
        return downloadId
    }
}

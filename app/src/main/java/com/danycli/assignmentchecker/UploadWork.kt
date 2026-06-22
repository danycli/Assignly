package com.danycli.assignmentchecker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val UPLOAD_WORK_PREFIX = "assignly-upload-"

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uploadId = inputData.getString(KEY_UPLOAD_ID).orEmpty()
        val submitPageUrl = inputData.getString(KEY_SUBMIT_PAGE_URL).orEmpty()
        val fileUri = inputData.getString(KEY_FILE_URI).orEmpty()
        val assignmentTitle = inputData.getString(KEY_ASSIGNMENT_TITLE).orEmpty()
        if (uploadId.isBlank() || submitPageUrl.isBlank() || fileUri.isBlank()) {
            return Result.failure()
        }

        UploadQueueStore.updateStatus(applicationContext, uploadId, UploadQueueStatus.RUNNING)
        val settings = AppSettingsStore.get(applicationContext)
        if (settings.uploadNotificationsEnabled) {
            UploadNotifier.showProgress(applicationContext, uploadId, assignmentTitle.ifBlank { "Assignment" })
        }

        val credentials = CredentialsStore.get(applicationContext)
            ?: return failUpload(uploadId, assignmentTitle, "Missing saved credentials.")

        val (username, password) = credentials
        val repository = PortalRepository()
        val file = runCatching { persistUriToTempFile(Uri.parse(fileUri), uploadId) }.getOrNull()
            ?: return failUpload(uploadId, assignmentTitle, "Cannot read selected file.")

        return try {
            when (repository.login(username, password)) {
                is LoginResult.Success -> {
                    when (val uploadResult = repository.uploadAssignment(submitPageUrl, file)) {
                        is UploadResult.Success -> {
                            UploadQueueStore.updateStatus(applicationContext, uploadId, UploadQueueStatus.SUCCESS)
                            if (settings.uploadNotificationsEnabled) {
                                UploadNotifier.showSuccess(applicationContext, uploadId, assignmentTitle.ifBlank { "Assignment" })
                            }
                            runCatching {
                                val (pending, historical) = repository.fetchAssignments()
                                AssignmentCacheStore.saveSnapshot(
                                    context = applicationContext,
                                    pendingAssignments = pending,
                                    historicalAssignments = historical,
                                    studentName = repository.getCurrentStudentName()
                                )
                            }
                            Result.success()
                        }
                        is UploadResult.NetworkError,
                        is UploadResult.Timeout -> retryUpload(uploadId, assignmentTitle, "Network issue while uploading.")

                        is UploadResult.Rejected -> failUpload(uploadId, assignmentTitle, uploadResult.reason)
                        is UploadResult.Error -> failUpload(uploadId, assignmentTitle, uploadResult.message)
                    }
                }
                is LoginResult.InvalidCredentials -> failUpload(uploadId, assignmentTitle, "Session expired. Please log in again.")
                is LoginResult.CaptchaRequired -> retryUpload(uploadId, assignmentTitle, "CAPTCHA required.")
                is LoginResult.Error -> retryUpload(uploadId, assignmentTitle, "Login failed in background upload.")
            }
        } catch (io: IOException) {
            retryUpload(uploadId, assignmentTitle, "Network error.")
        } catch (e: Exception) {
            Log.e("UploadWorker", "Upload failed: ${e.message}", e)
            failUpload(uploadId, assignmentTitle, e.message ?: "Unexpected upload error.")
        } finally {
            runCatching { file.delete() }
        }
    }

    private fun persistUriToTempFile(uri: Uri, uploadId: String): File {
        val originalName = getFileNameFromUri(applicationContext, uri) ?: "queued_upload"
        val ext = originalName.substringAfterLast('.', "bin")
        val safeExt = ext.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val tempFile = File(applicationContext.cacheDir, "queued_upload_${uploadId}.${safeExt}")
        applicationContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Input stream unavailable." }
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    private fun retryUpload(uploadId: String, assignmentTitle: String, reason: String): Result {
        UploadQueueStore.updateStatus(applicationContext, uploadId, UploadQueueStatus.FAILED_RETRY, reason)
        val settings = AppSettingsStore.get(applicationContext)
        if (settings.uploadNotificationsEnabled) {
            UploadNotifier.showProgress(applicationContext, uploadId, assignmentTitle.ifBlank { "Assignment" }, "Retrying soon…")
        }
        return Result.retry()
    }

    private fun failUpload(uploadId: String, assignmentTitle: String, reason: String): Result {
        UploadQueueStore.updateStatus(applicationContext, uploadId, UploadQueueStatus.FAILED, reason)
        val settings = AppSettingsStore.get(applicationContext)
        if (settings.uploadNotificationsEnabled) {
            UploadNotifier.showFailure(applicationContext, uploadId, assignmentTitle.ifBlank { "Assignment" }, reason)
        }
        return Result.failure(
            Data.Builder()
                .putString(KEY_ERROR_MESSAGE, reason)
                .build()
        )
    }

    companion object {
        const val KEY_UPLOAD_ID = "upload_id"
        const val KEY_SUBMIT_PAGE_URL = "submit_page_url"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_ASSIGNMENT_TITLE = "assignment_title"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}

object UploadWorkScheduler {
    fun enqueue(
        context: Context,
        assignmentTitle: String,
        submitPageUrl: String,
        fileUri: Uri
    ): String {
        val uploadId = UUID.randomUUID().toString()
        UploadQueueStore.upsert(
            context = context,
            upload = QueuedUpload(
                id = uploadId,
                assignmentTitle = assignmentTitle,
                fileUri = fileUri.toString(),
                submitPageUrl = submitPageUrl,
                status = UploadQueueStatus.QUEUED,
                lastError = null,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(UploadWorker.KEY_UPLOAD_ID, uploadId)
                    .putString(UploadWorker.KEY_SUBMIT_PAGE_URL, submitPageUrl)
                    .putString(UploadWorker.KEY_FILE_URI, fileUri.toString())
                    .putString(UploadWorker.KEY_ASSIGNMENT_TITLE, assignmentTitle)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return uploadId
    }
}

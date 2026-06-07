package com.danycli.assignmentchecker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PERIODIC_SYNC_WORK_NAME = "assignly_periodic_sync_work"

object BackgroundSyncScheduler {
    fun applySettings(context: Context, settings: AppSettings = AppSettingsStore.get(context)) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.backgroundSyncEnabled) {
            workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<AssignmentSyncWorker>(
            settings.syncIntervalHours,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class AssignmentSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val updateInfo = withContext(Dispatchers.IO) { fetchAppUpdateInfo() }
        val settings = AppSettingsStore.get(applicationContext)
        if (settings.updateNotificationsEnabled &&
            updateInfo != null &&
            updateInfo.latestVersionCode > BuildConfig.VERSION_CODE
        ) {
            UpdateNotifier.maybeNotify(applicationContext, updateInfo)
        }

        val credentials = CredentialsStore.get(applicationContext)
            ?: return Result.failure()

        val (username, password) = credentials
        val repository = PortalRepository()

        return try {
            when (repository.login(username, password)) {
                is LoginResult.Success -> {
                    val previousSnapshot = AssignmentCacheStore.loadSnapshot(applicationContext)
                    val (pending, historical) = repository.fetchAssignments()
                    if (settings.assignmentNotificationsEnabled) {
                        val newAssignments = detectNewAssignments(previousSnapshot, pending, historical)
                        val notifiedKeys = AssignmentNotificationStore.getNotifiedKeys(applicationContext)
                        val unseenAssignments = newAssignments.filter {
                            assignmentNotificationKey(it) !in notifiedKeys
                        }
                        if (unseenAssignments.isNotEmpty()) {
                            AssignmentNotificationManager.notifyNewAssignments(applicationContext, unseenAssignments)
                            AssignmentNotificationStore.markNotified(
                                applicationContext,
                                unseenAssignments.map { assignmentNotificationKey(it) }
                            )
                        }
                        AssignmentNotificationManager.scheduleDeadlineReminders(
                            applicationContext,
                            pending,
                            historical
                        )
                    }
                    AssignmentCacheStore.saveSnapshot(
                        context = applicationContext,
                        pendingAssignments = pending,
                        historicalAssignments = historical,
                        studentName = repository.getCurrentStudentName()
                    )
                    
                    val fetchedTimetable = repository.fetchTimetable()
                    if (fetchedTimetable.isNotEmpty()) {
                        TimetableCacheStore.saveSnapshot(applicationContext, fetchedTimetable)
                    }

                    Result.success()
                }
                is LoginResult.InvalidCredentials -> Result.failure()
                is LoginResult.CaptchaRequired -> Result.failure()
                is LoginResult.Error -> Result.retry()
            }
        } catch (io: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Log.e("AssignmentSyncWorker", "Periodic sync failed: ${e.message}", e)
            Result.failure()
        }
    }
}

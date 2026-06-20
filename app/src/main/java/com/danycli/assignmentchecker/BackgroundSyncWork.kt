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

                    // Fetch and sync student profile & photo in the background
                    runCatching {
                        val prof = repository.fetchStudentProfile()
                        ProfileCacheStore.saveSnapshot(applicationContext, prof)
                        val photo = repository.fetchCurrentStudentPhoto()
                        if (photo != null) {
                            ProfileCacheStore.savePhoto(applicationContext, photo)
                        }
                    }.onFailure { err ->
                        Log.e("AssignmentSyncWorker", "Failed to background-fetch profile: ${err.message}")
                    }

                    // Fetch and sync assessment marks in the background if marks notifications are enabled
                    if (settings.marksNotificationsEnabled) {
                        try {
                            val cachedAttendance = AttendanceCacheStore.loadSnapshot(applicationContext)
                            val courses = cachedAttendance?.summary?.map { it.courseCode } ?: emptyList()
                            val courseNamesMap = cachedAttendance?.summary?.associate { it.courseCode to it.courseName } ?: emptyMap()
                            
                            if (courses.isNotEmpty()) {
                                val previousMarksSnapshot = MarksCacheStore.loadSnapshot(applicationContext)
                                val previousMarksList = previousMarksSnapshot?.courseMarksList ?: emptyList()
                                val previousMarksMap = previousMarksList.associateBy { it.courseCode.trim().uppercase() }

                                val fetchedMarks = mutableListOf<CourseMarks>()
                                for (code in courses) {
                                    val cleanCode = code.trim().uppercase()
                                    val cachedCourse = previousMarksMap[cleanCode]

                                    runCatching {
                                        val categories = repository.fetchMarks(code)
                                        val name = courseNamesMap[code] ?: ""
                                        if (categories.isEmpty() && cachedCourse != null && cachedCourse.categories.isNotEmpty()) {
                                            Log.w("AssignmentSyncWorker", "Fetched empty marks categories for $code, but cache has data. Retaining cached marks.")
                                            fetchedMarks.add(cachedCourse)
                                        } else {
                                            fetchedMarks.add(CourseMarks(code, name, categories))
                                        }
                                    }.onFailure { err ->
                                        Log.e("AssignmentSyncWorker", "Failed to background-fetch marks for $code: ${err.message}")
                                        if (cachedCourse != null) {
                                            Log.i("AssignmentSyncWorker", "Retaining cached marks for $code due to fetch failure.")
                                            fetchedMarks.add(cachedCourse)
                                        }
                                    }
                                }
                                if (fetchedMarks.isNotEmpty()) {
                                    val changes = detectMarksChanges(previousMarksList, fetchedMarks)
                                    if (changes.isNotEmpty()) {
                                        MarksNotificationManager.notifyMarksChanges(applicationContext, changes)
                                    }
                                    
                                    val updatedCourseCodes = courses.map { it.trim().uppercase() }.toSet()
                                    val otherMarks = previousMarksList.filter { cachedMarks ->
                                        cachedMarks.courseCode.trim().uppercase() !in updatedCourseCodes
                                    }
                                    val mergedMarks = otherMarks + fetchedMarks
                                    MarksCacheStore.saveSnapshot(applicationContext, mergedMarks)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AssignmentSyncWorker", "Failed to sync marks in background: ${e.message}", e)
                        }
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

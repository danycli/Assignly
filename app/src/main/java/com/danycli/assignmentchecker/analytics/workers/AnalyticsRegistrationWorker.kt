package com.danycli.assignmentchecker.analytics.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danycli.assignmentchecker.analytics.AnalyticsManager
import com.danycli.assignmentchecker.analytics.AnalyticsRepository
import com.danycli.assignmentchecker.analytics.InstallationManager

class AnalyticsRegistrationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val installationManager = InstallationManager(applicationContext)
        val repository = AnalyticsRepository(installationManager)

        if (installationManager.isRegistered()) {
            return Result.success()
        }

        val success = repository.registerInstallation()

        return if (success) {
            // Trigger immediate heartbeat after registration
            AnalyticsManager.scheduleImmediateHeartbeat(applicationContext)
            // Schedule daily heartbeats
            AnalyticsManager.scheduleDailyHeartbeats(applicationContext)
            Result.success()
        } else {
            Result.retry() // Retry on network failure
        }
    }
}

package com.danycli.assignmentchecker.analytics.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danycli.assignmentchecker.analytics.AnalyticsRepository
import com.danycli.assignmentchecker.analytics.InstallationManager

class AnalyticsHeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val installationManager = InstallationManager(applicationContext)
        val repository = AnalyticsRepository(installationManager)

        // Don't heartbeat if not registered
        if (!installationManager.isRegistered()) {
            return Result.failure()
        }

        val success = repository.sendHeartbeat()

        return if (success) {
            Result.success()
        } else {
            Result.retry() // Retry on network failure
        }
    }
}

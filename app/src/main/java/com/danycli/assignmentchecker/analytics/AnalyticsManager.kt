package com.danycli.assignmentchecker.analytics

import android.content.Context
import androidx.work.*
import com.danycli.assignmentchecker.analytics.workers.AnalyticsRegistrationWorker
import com.danycli.assignmentchecker.analytics.workers.AnalyticsHeartbeatWorker
import java.util.concurrent.TimeUnit

object AnalyticsManager {

    fun initialize(context: Context) {
        val installationManager = InstallationManager(context)
        
        // Touch the UUID/time generators early to ensure they are seeded
        installationManager.getInstallationId()
        installationManager.getFirstInstallTime()

        if (!installationManager.isRegistered()) {
            scheduleRegistration(context)
        } else {
            // Already registered, ensure heartbeats are scheduled
            scheduleImmediateHeartbeat(context)
            scheduleDailyHeartbeats(context)
        }
    }

    private fun scheduleRegistration(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AnalyticsRegistrationWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "AnalyticsRegistration",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    internal fun scheduleImmediateHeartbeat(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AnalyticsHeartbeatWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "AnalyticsImmediateHeartbeat",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    internal fun scheduleDailyHeartbeats(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AnalyticsHeartbeatWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AnalyticsDailyHeartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

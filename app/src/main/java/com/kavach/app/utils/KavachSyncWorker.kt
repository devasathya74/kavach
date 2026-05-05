package com.kavach.app.utils

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that flushes both:
 *  1. Offline order acknowledgment queue → server
 *  2. Buffered behavior events → server
 *
 * Runs every 15 minutes when network is available.
 * Uses exponential backoff on failure.
 */
@HiltWorker
class KavachSyncWorker @AssistedInject constructor(
    @Assisted context                      : Context,
    @Assisted workerParams                 : WorkerParameters,
    private val behaviorTracker            : BehaviorTracker,
    private val orderRepository            : com.kavach.app.data.remote.repository.OrderRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        var overallSuccess = true

        // ── 1. Sync order acknowledgments ──────────────────
        when (orderRepository.syncPendingAcknowledgments()) {
            is Resource.Error -> overallSuccess = false
            else              -> Unit
        }

        // ── 2. Sync behavior events ────────────────────────
        when (behaviorTracker.syncToServer()) {
            is Resource.Error -> overallSuccess = false
            else              -> Unit
        }

        return if (overallSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "kavach_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<KavachSyncWorker>(
                repeatInterval         = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

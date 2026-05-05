package com.kavach.app.utils

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.remote.repository.OrderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that flushes pending offline order acknowledgments.
 * Runs periodically in background when network is available.
 */
@HiltWorker
class SyncAckWorker @AssistedInject constructor(
    @Assisted context        : Context,
    @Assisted workerParams   : WorkerParameters,
    private val orderRepository: OrderRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return when (orderRepository.syncPendingAcknowledgments()) {
            is Resource.Success -> Result.success()
            is Resource.Error   -> Result.retry()
            else -> Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "kavach_ack_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncAckWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

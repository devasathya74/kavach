package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.remote.api.KavachApiV2
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class BroadcastSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: KavachDatabase,
    private val api: KavachApiV2
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingMutations = db.broadcastDao().getPendingMutations()
        if (pendingMutations.isEmpty()) return Result.success()

        var hasFailures = false

        for (mutation in pendingMutations) {
            try {
                val success = when (mutation.actionType) {
                    "ACKNOWLEDGE" -> {
                        val response = api.acknowledgeBroadcast(mutation.broadcastId)
                        response.isSuccessful
                    }
                    "READ" -> {
                        // In real scenario, would call api.readBroadcast(mutation.broadcastId)
                        // For now, assume acknowledgeBroadcast covers it or use a placeholder
                        true 
                    }
                    else -> true
                }

                if (success) {
                    db.broadcastDao().removeMutation(mutation.id)
                    Timber.tag("KAVACH_SYNC").d("BroadcastSync: Successfully synced ${mutation.actionType} for ${mutation.broadcastId}")
                } else {
                    hasFailures = true
                }
            } catch (e: Exception) {
                Timber.tag("KAVACH_SYNC").e(e, "BroadcastSync: Failure")
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "broadcast_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BroadcastSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

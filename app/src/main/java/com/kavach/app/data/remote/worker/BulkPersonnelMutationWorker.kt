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
class BulkPersonnelMutationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: KavachDatabase,
    private val api: KavachApiV2
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingMutations = db.officerDao().getPendingBulkMutations()
        if (pendingMutations.isEmpty()) return Result.success()

        var overallFailure = false

        for (mutation in pendingMutations) {
            try {
                db.officerDao().updateBulkMutationStatus(mutation.correlationId, "PROCESSING")
                
                val ids = mutation.targetIds.split(",")
                val chunks = ids.chunked(20)
                
                var mutationFailed = false
                
                for (chunk in chunks) {
                    // In real API, we might have a bulk endpoint. 
                    // If not, we process them one by one or in smaller batches.
                    // Assuming for now we call single endpoints or a hypothesized bulk one.
                    val success = when (mutation.actionType) {
                        "DELETE" -> {
                            // Placeholder for bulk delete API or sequential calls
                            chunk.all { id -> api.deleteUser(id).isSuccessful }
                        }
                        "BLOCK" -> {
                            chunk.all { id -> api.deactivateUser(id, com.kavach.app.data.remote.dto.v2.GenericIdRequest(reason = "Bulk block")).isSuccessful }
                        }
                        else -> true
                    }
                    
                    if (!success) {
                        mutationFailed = true
                        break
                    }
                }
                
                if (mutationFailed) {
                    db.officerDao().updateBulkMutationStatus(mutation.correlationId, "FAILED")
                    overallFailure = true
                } else {
                    db.officerDao().deleteBulkMutation(mutation.correlationId)
                    Timber.tag("KAVACH_BULK").i("Bulk action ${mutation.actionType} completed for correlation ${mutation.correlationId}")
                }
                
            } catch (e: Exception) {
                Timber.tag("KAVACH_BULK").e(e, "Failure processing bulk mutation ${mutation.correlationId}")
                db.officerDao().updateBulkMutationStatus(mutation.correlationId, "FAILED")
                overallFailure = true
            }
        }

        return if (overallFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "bulk_personnel_mutation_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BulkPersonnelMutationWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}

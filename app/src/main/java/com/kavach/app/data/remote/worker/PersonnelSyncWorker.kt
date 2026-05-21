package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.repository.UserManagementRepository
import com.kavach.app.utils.ApiResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class PersonnelSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: UserManagementRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting background personnel sync (page 1)...")
            val result = repository.refreshUsers(page = 1)
            
            when (result) {
                is ApiResult.Error -> {
                    Timber.e("Background personnel sync failed: ${result.message}")
                    throw Exception(result.message)
                }
                is ApiResult.Unauthorized -> {
                    Timber.e("Background sync UNAUTHORIZED. Stopping.")
                }
                else -> {}
            }
            Timber.d("Background personnel sync successful.")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "Background sync scheduled for retry (Attempt $runAttemptCount)")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "personnel_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PersonnelSyncWorker>(
                repeatInterval = 1, // Every hour (approx)
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

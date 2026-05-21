package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.GenericIdRequest
import com.kavach.app.data.remote.dto.v2.UpdateUserRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class PersonnelMutationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: KavachDatabase,
    private val api: KavachApiV2
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingActions = db.officerDao().getPendingPersonnelActions()
        if (pendingActions.isEmpty()) return Result.success()

        var hasFailures = false

        for (action in pendingActions) {
            try {
                val success = when (action.actionType) {
                    "DELETE" -> {
                        val resp = api.updateUser(action.officerId, UpdateUserRequest(isActive = false))
                        resp.isSuccessful
                    }
                    "BLOCK" -> {
                        val resp = api.deactivateUser(action.officerId, GenericIdRequest(reason = "System Blocked"))
                        resp.isSuccessful
                    }
                    "UNBLOCK" -> {
                        val resp = api.updateUser(action.officerId, UpdateUserRequest(isActive = true))
                        resp.isSuccessful
                    }
                    else -> true
                }

                if (success) {
                    db.officerDao().deletePersonnelAction(action.officerId)
                    Timber.d("PersonnelMutationWorker: Action ${action.actionType} successful for ${action.officerId}")
                } else {
                    Timber.e("PersonnelMutationWorker: Server rejected action ${action.actionType} for ${action.officerId}")
                    hasFailures = true
                }
            } catch (e: Exception) {
                Timber.e(e, "PersonnelMutationWorker: Error processing action")
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "personnel_mutation_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PersonnelMutationWorker>()
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

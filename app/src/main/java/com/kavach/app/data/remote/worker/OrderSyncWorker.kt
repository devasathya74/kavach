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
class OrderSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: KavachDatabase,
    private val api: KavachApiV2
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingAcks = db.orderDao().getPendingAcks()
        if (pendingAcks.isEmpty()) return Result.success()

        var hasFailures = false

        for (ack in pendingAcks) {
            try {
                val response = api.acknowledgeOrder(ack.orderId)
                if (response.isSuccessful && response.body()?.status == "success") {
                    db.orderDao().clearDirtyFlag(ack.orderId)
                    db.orderDao().removeAck(ack.orderId)
                    Timber.d("OrderSyncWorker: Acknowledged order ${ack.orderId}")
                } else {
                    Timber.e("OrderSyncWorker: Server rejected ACK for ${ack.orderId}")
                    hasFailures = true
                }
            } catch (e: Exception) {
                Timber.e(e, "OrderSyncWorker: Network failure for ${ack.orderId}")
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "order_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OrderSyncWorker>()
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

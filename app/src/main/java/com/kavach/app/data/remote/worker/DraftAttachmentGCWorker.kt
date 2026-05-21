package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class DraftAttachmentGCWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: KavachDatabase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Cleanup rule: Orphans or local attachments older than 24h
        val threshold = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        
        return try {
            db.incidentDao().cleanupOrphanAttachments(threshold)
            Timber.tag("KAVACH_GC").i("Orphan attachment cleanup completed")
            Result.success()
        } catch (e: Exception) {
            Timber.tag("KAVACH_GC").e(e, "GC cleanup failed")
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "draft_attachment_gc_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DraftAttachmentGCWorker>(1, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

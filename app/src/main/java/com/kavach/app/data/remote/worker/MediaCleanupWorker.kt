package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MediaCleanupWorker — Resiliency job to prevent storage leaks and orphan media.
 * 
 * Logic:
 * 1. Scans local 'incidents' media directory.
 * 2. Compares with IncidentAttachmentEntity in Room.
 * 3. Deletes files that are either:
 *    a) Not in DB (Orphans).
 *    b) In DB but marked as COMPLETED (Remote sync successful, local cache purge).
 *    c) In DB but incident itself is RESOLVED (Deep cleanup).
 */
@HiltWorker
class MediaCleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val db: KavachDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.tag("KAVACH_SYNC").i("Starting Media Cleanup Job...")
        
        try {
            val mediaDir = File(context.filesDir, "media/incidents")
            if (!mediaDir.exists()) return Result.success()

            val localFiles = mediaDir.listFiles() ?: return Result.success()
            val attachmentsInDb = db.incidentDao().getAllAttachments()
            
            val dbUris = attachmentsInDb.mapNotNull { it.localUri }
            
            var cleanedCount = 0
            
            for (file in localFiles) {
                val fileUri = file.absolutePath
                val attachment = attachmentsInDb.find { it.localUri == fileUri }
                
                val shouldDelete = when {
                    attachment == null -> {
                        Timber.tag("KAVACH_SYNC").d("Cleanup: Orphan file found (not in DB): ${file.name}")
                        true
                    }
                    attachment.status == "COMPLETED" -> {
                        Timber.tag("KAVACH_SYNC").d("Cleanup: Purging local cache for synced attachment: ${file.name}")
                        true
                    }
                    else -> false
                }

                if (shouldDelete) {
                    if (file.delete()) cleanedCount++
                }
            }

            Timber.tag("KAVACH_SYNC").i("Media Cleanup Finished. Removed $cleanedCount files.")
            return Result.success()
        } catch (e: Exception) {
            Timber.tag("KAVACH_SYNC").e(e, "Media Cleanup Failed")
            return Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "media_cleanup_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MediaCleanupWorker>(
                repeatInterval = 12,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

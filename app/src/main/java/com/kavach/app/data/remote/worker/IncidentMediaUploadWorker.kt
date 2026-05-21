package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.remote.api.KavachApiV2
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File

@HiltWorker
class IncidentMediaUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: KavachDatabase,
    private val api: KavachApiV2
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Constrained Upload Queue: Process one at a time to prevent "Upload Storm"
        val next = db.incidentDao().getNextQueuedAttachment() ?: return Result.success()

        try {
            db.incidentDao().updateAttachmentStatus(next.localId, "UPLOADING", 0)
            
            val file = File(next.localUri ?: "")
            if (!file.exists()) {
                db.incidentDao().updateAttachmentStatus(next.localId, "FAILED", 0)
                return Result.failure()
            }

            val requestFile = file.asRequestBody(next.mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            val response = api.uploadFieldData(
                file = body,
                title = next.incidentLocalId.toRequestBody("text/plain".toMediaTypeOrNull()),
                category = "INCIDENT_MEDIA".toRequestBody("text/plain".toMediaTypeOrNull())
            )
            
            if (response.isSuccessful && response.body() != null && response.body()!!.data != null) {
                val remoteUrl = response.body()!!.data!!.fileUrl ?: ""
                db.incidentDao().updateAttachment(
                    next.copy(
                        status = "UPLOADED",
                        remoteUrl = remoteUrl,
                        uploadProgress = 100
                    )
                )
                
                // Chain next upload
                schedule(applicationContext)
                return Result.success()
            } else {
                db.incidentDao().updateAttachmentStatus(next.localId, "FAILED", 0)
                return Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "Media upload failed for ${next.localId}")
            db.incidentDao().updateAttachmentStatus(next.localId, "FAILED", 0)
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "incident_media_upload_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<IncidentMediaUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

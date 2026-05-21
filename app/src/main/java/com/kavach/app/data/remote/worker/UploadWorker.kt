package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.BroadcastFileManager
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.entity.BroadcastAttachmentEntity
import com.kavach.app.data.remote.api.KavachApiV2
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File

/**
 * UploadWorker — Uploads broadcast attachments from app-private storage to the backend.
 *
 * SAFETY RULES:
 * 1. Only reads files from app-private storage (absolute path — never content://)
 * 2. Computes SHA-256 checksum before upload for deduplication
 * 3. If same checksum already uploaded → reuse remote URL, skip network call
 * 4. On success → updates DB with remoteUrl + checksum, chains BroadcastDispatchWorker
 * 5. On failure → marks attachment as FAILED, returns Result.retry() for WorkManager backoff
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val broadcastDao: BroadcastDao,
    private val api: KavachApiV2,
    private val fileManager: BroadcastFileManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dispatchId = inputData.getString(KEY_DISPATCH_ID)
            ?: return@withContext Result.failure()

        try {
            val jobs = broadcastDao.getPendingDispatches()
            val currentJob = jobs.find { it.dispatchId == dispatchId }

            if (currentJob == null) {
                Timber.e("UploadWorker: Dispatch job $dispatchId not found")
                return@withContext Result.failure()
            }

            val draft = broadcastDao.getDraft(currentJob.draftId)
            if (draft == null) {
                Timber.e("UploadWorker: Draft not found for job $dispatchId")
                return@withContext Result.failure()
            }

            val attachments = broadcastDao.getAttachments(draft.draftId)
            var allSuccessful = true

            for (attachment in attachments) {
                if (attachment.uploadStatus == "UPLOADED") {
                    Timber.d("UploadWorker: Attachment ${attachment.localId} already uploaded, skipping")
                    continue
                }

                val privatePath = attachment.uri // stored as absolute private path
                val file = File(privatePath)

                if (!file.exists()) {
                    Timber.e("UploadWorker: Private file not found: $privatePath")
                    broadcastDao.insertAttachment(attachment.copy(uploadStatus = "FAILED"))
                    allSuccessful = false
                    continue
                }

                // ── Checksum Deduplication ────────────────────────────────
                val checksum = try {
                    fileManager.computeChecksum(privatePath)
                } catch (e: Exception) {
                    Timber.e(e, "UploadWorker: Checksum failed for $privatePath")
                    broadcastDao.insertAttachment(attachment.copy(uploadStatus = "FAILED"))
                    allSuccessful = false
                    continue
                }

                // Check if already uploaded with same hash — skip network call
                val existing = broadcastDao.getAttachmentByChecksum(checksum)
                if (existing?.uploadStatus == "UPLOADED" && existing.remoteUrl != null) {
                    Timber.d("UploadWorker: Checksum match — reusing remote URL for ${attachment.localId}")
                    broadcastDao.insertAttachment(
                        attachment.copy(
                            uploadStatus = "UPLOADED",
                            remoteUrl = existing.remoteUrl,
                            checksum = checksum
                        )
                    )
                    continue
                }
                // ─────────────────────────────────────────────────────────

                // Mark as UPLOADING
                broadcastDao.insertAttachment(attachment.copy(uploadStatus = "UPLOADING", checksum = checksum))

                try {
                    val requestFile = file.asRequestBody(attachment.mimeType.toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    val response = api.uploadAttachment(body)

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        broadcastDao.insertAttachment(
                            attachment.copy(
                                uploadStatus = "UPLOADED",
                                remoteUrl = body.remote_url,
                                checksum = checksum
                            )
                        )
                        Timber.d("UploadWorker: Uploaded ${attachment.localId} → ${body.remote_url}")
                    } else {
                        throw Exception("Upload API returned ${response.code()}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "UploadWorker: Upload failed for ${attachment.localId}")
                    broadcastDao.insertAttachment(
                        attachment.copy(uploadStatus = "FAILED", checksum = checksum)
                    )
                    allSuccessful = false
                }
            }

            return@withContext if (allSuccessful) {
                broadcastDao.updateDispatchStatus(dispatchId, "READY_FOR_DISPATCH")
                BroadcastDispatchWorker.enqueue(context, dispatchId)
                Result.success()
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "UploadWorker: Unexpected failure for dispatchId=$dispatchId")
            return@withContext Result.retry()
        }
    }

    companion object {
        private const val KEY_DISPATCH_ID = "dispatchId"

        fun enqueue(context: Context, dispatchId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_DISPATCH_ID to dispatchId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("upload_$dispatchId", ExistingWorkPolicy.KEEP, request)
        }
    }
}

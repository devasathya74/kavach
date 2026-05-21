package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.dao.BroadcastDao
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
import java.net.URI

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val broadcastDao: BroadcastDao,
    private val api: KavachApiV2
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dispatchId = inputData.getString(KEY_DISPATCH_ID) ?: return@withContext Result.failure()
        
        try {
            // Find the dispatch job
            val jobs = broadcastDao.getPendingDispatches()
            val currentJob = jobs.find { it.dispatchId == dispatchId }
            
            if (currentJob == null) {
                Timber.e("Dispatch Job $dispatchId not found")
                return@withContext Result.failure()
            }

            // Get the draft to find local ID
            val draft = broadcastDao.getDraft(currentJob.draftId)
            if (draft == null) {
                Timber.e("Draft not found for job $dispatchId")
                return@withContext Result.failure()
            }

            // Get attachments for this draft
            val attachments = broadcastDao.getAttachments(draft.draftId)
            var allSuccessful = true

            for (attachment in attachments) {
                if (attachment.uploadStatus == "UPLOADED") continue

                // Update status to UPLOADING
                val uploadingAtt = attachment.copy(uploadStatus = "UPLOADING")
                broadcastDao.insertAttachment(uploadingAtt)

                try {
                    val file = File(URI(attachment.uri))
                    if (!file.exists()) {
                        throw Exception("File not found at ${attachment.uri}")
                    }

                    val requestFile = file.asRequestBody(attachment.mimeType.toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    // Call the upload API (which we added to BroadcastViewSet)
                    val response = api.uploadAttachment(body)

                    if (response.isSuccessful && response.body() != null) {
                        val bodyData = response.body()!!
                        val remoteUrl = bodyData.remote_url
                        val checksum = bodyData.checksum

                        val uploadedAtt = uploadingAtt.copy(
                            uploadStatus = "UPLOADED",
                            remoteUrl = remoteUrl,
                            checksum = checksum
                        )
                        broadcastDao.insertAttachment(uploadedAtt)
                    } else {
                        throw Exception("Upload failed with code: ${response.code()}")
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Failed to upload attachment ${attachment.localId}")
                    allSuccessful = false
                    val failedAtt = uploadingAtt.copy(uploadStatus = "FAILED")
                    broadcastDao.insertAttachment(failedAtt)
                }
            }

            if (allSuccessful) {
                // If all attachments uploaded, we can move the dispatch job state to READY_FOR_DISPATCH
                // and chain the Dispatch Worker
                broadcastDao.updateDispatchStatus(dispatchId, "READY_FOR_DISPATCH")
                BroadcastDispatchWorker.enqueue(context, dispatchId)
                return@withContext Result.success()
            } else {
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "UploadWorker failed")
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
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("upload_$dispatchId", ExistingWorkPolicy.KEEP, request)
        }
    }
}

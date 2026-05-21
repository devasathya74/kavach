package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.FinalizeBroadcastRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber

@HiltWorker
class BroadcastDispatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val broadcastDao: BroadcastDao,
    private val api: KavachApiV2
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dispatchId = inputData.getString(KEY_DISPATCH_ID) ?: return@withContext Result.failure()

        try {
            val jobs = broadcastDao.getPendingDispatches()
            val job = jobs.find { it.dispatchId == dispatchId } ?: return@withContext Result.failure()

            if (job.status == "DISPATCHING") {
                // Prevent duplicate processing
                return@withContext Result.retry()
            }

            val draft = broadcastDao.getDraft(job.draftId)
            if (draft == null) {
                Timber.e("Draft not found for dispatch ${job.dispatchId}")
                broadcastDao.updateDispatchStatus(dispatchId, "FAILED", "Draft not found")
                return@withContext Result.failure()
            }

            val attachments = broadcastDao.getAttachments(draft.draftId)
            
            // Check if all attachments are uploaded
            val hasPending = attachments.any { it.uploadStatus != "UPLOADED" }
            if (hasPending) {
                // Enqueue upload worker and finish this one.
                // UploadWorker will trigger DispatchWorker again when done.
                broadcastDao.updateDispatchStatus(dispatchId, "UPLOADING")
                UploadWorker.enqueue(context, dispatchId)
                return@withContext Result.success()
            }

            // Move to dispatching state
            broadcastDao.updateDispatchStatus(dispatchId, "DISPATCHING")

            // Parse selected recipients
            val recipientIds = mutableListOf<String>()
            try {
                val array = JSONArray(draft.selectedUserIdsJson)
                for (i in 0 until array.length()) {
                    recipientIds.add(array.getString(i))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse recipient IDs")
                broadcastDao.updateDispatchStatus(dispatchId, "FAILED", "Invalid recipient JSON")
                return@withContext Result.failure()
            }

            // Map attachments
            val attachmentsList = attachments.map {
                mapOf(
                    "file_name" to (it.uri.substringAfterLast('/')),
                    "mime_type" to it.mimeType,
                    "file_size" to 0, // Should be actual size, mapped in DB if needed
                    "remote_url" to (it.remoteUrl ?: ""),
                    "checksum" to (it.checksum ?: "")
                )
            }

            val request = FinalizeBroadcastRequest(
                title = draft.title,
                content = draft.content,
                priority = draft.priority,
                type = draft.type,
                traceId = job.correlationId,
                recipientIds = recipientIds,
                attachments = attachmentsList
            )

            val response = api.finalizeBroadcast(request)

            if (response.isSuccessful) {
                // Success! Clean up
                broadcastDao.updateDispatchStatus(dispatchId, "COMPLETED")
                broadcastDao.deleteDraft(draft.draftId)
                // The newly created broadcast will sync down via WebSocket or normal refresh
                return@withContext Result.success()
            } else {
                val errorMsg = "API Error: ${response.code()}"
                broadcastDao.updateDispatchStatus(dispatchId, "FAILED", errorMsg)
                Timber.e(errorMsg)
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "BroadcastDispatchWorker failed")
            broadcastDao.updateDispatchStatus(dispatchId, "FAILED", e.localizedMessage)
            return@withContext Result.retry()
        }
    }

    companion object {
        private const val KEY_DISPATCH_ID = "dispatchId"

        fun enqueue(context: Context, dispatchId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BroadcastDispatchWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_DISPATCH_ID to dispatchId))
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("dispatch_$dispatchId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

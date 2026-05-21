package com.kavach.app.data.remote.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.dao.UserIncidentDao
import com.kavach.app.data.local.entity.UserIncidentDraftEntity
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.CreateIncidentRequest
import com.kavach.app.data.repository.MediaRepository
import com.kavach.app.utils.ApiResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * UserIncidentSyncWorker — Isolated background incident draft sync worker.
 *
 * Keeps USER runtime decoupled from PILOT database (strictly uses userIncidentDao).
 *
 * Flow:
 * 1. Read pending drafts from DB
 * 2. Upload any attachments first to MediaRepository
 * 3. Update attachment status with remote URL
 * 4. Submit incident report draft to backend with media URL
 * 5. Mark draft as SYNCED
 */
@HiltWorker
class UserIncidentSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userIncidentDao: UserIncidentDao,
    private val api: KavachApiV2,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("UserIncidentSyncWorker: Starting sync loop")
        val pendingDrafts = userIncidentDao.getPendingDraftsList()

        if (pendingDrafts.isEmpty()) {
            Timber.d("UserIncidentSyncWorker: No pending drafts to sync.")
            return Result.success()
        }

        var hasFailures = false

        for (draft in pendingDrafts) {
            val success = syncDraft(draft)
            if (!success) {
                hasFailures = true
            }
        }

        return if (hasFailures) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun syncDraft(draft: UserIncidentDraftEntity): Boolean {
        return try {
            // 1. Mark draft as uploading
            userIncidentDao.updateSyncStatus(draft.localId, "UPLOADING")

            // 2. Upload attachments (if any)
            val attachments = userIncidentDao.getAttachments(draft.localId)
            var attachmentsSuccess = true
            var firstMediaUrl: String? = null

            for (attachment in attachments) {
                if (attachment.uploadStatus == "UPLOADED") {
                    if (firstMediaUrl == null) {
                        firstMediaUrl = attachment.remoteUrl
                    }
                    continue
                }

                val file = File(attachment.localPath)
                if (!file.exists()) {
                    Timber.e("UserIncidentSyncWorker: Attachment file does not exist: ${attachment.localPath}")
                    userIncidentDao.updateAttachmentStatus(attachment.localId, "FAILED")
                    attachmentsSuccess = false
                    break
                }

                val uri = Uri.fromFile(file)
                userIncidentDao.updateAttachmentStatus(attachment.localId, "UPLOADING")

                val uploadResult = mediaRepository.uploadEvidence(
                    title = "Incident Report: ${draft.title}",
                    imageUri = uri,
                    category = "INCIDENT"
                )

                if (uploadResult is ApiResult.Success) {
                    val remoteUrl = uploadResult.data.fileUrl
                    userIncidentDao.updateAttachmentStatus(attachment.localId, "UPLOADED", remoteUrl)
                    if (firstMediaUrl == null) {
                        firstMediaUrl = remoteUrl
                    }
                    Timber.d("UserIncidentSyncWorker: Attachment uploaded successfully. url=$remoteUrl")
                } else {
                    Timber.e("UserIncidentSyncWorker: Attachment upload failed: ${(uploadResult as? ApiResult.Error)?.message}")
                    userIncidentDao.updateAttachmentStatus(attachment.localId, "FAILED")
                    attachmentsSuccess = false
                    break
                }
            }

            if (!attachmentsSuccess) {
                userIncidentDao.updateSyncStatus(draft.localId, "FAILED")
                return false
            }

            // 3. Submit draft incident to the backend
            val request = CreateIncidentRequest(
                title = draft.title,
                description = draft.description,
                summary = draft.description,
                severity = draft.severity,
                type = draft.type,
                occurredAt = java.time.Instant.ofEpochMilli(draft.createdAt).toString(),
                status = "OPEN",
                mediaUrl = firstMediaUrl
            )

            val response = api.createIncident(request)
            if (response.isSuccessful && response.body()?.status == "success") {
                val serverId = response.body()?.data?.id ?: ""
                userIncidentDao.markSynced(draft.localId, serverId)
                Timber.i("UserIncidentSyncWorker: Successfully synced draft ${draft.localId} -> serverId=$serverId")
                true
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Timber.e("UserIncidentSyncWorker: Server rejected draft ${draft.localId}. Error: $errorMsg")
                userIncidentDao.updateSyncStatus(draft.localId, "FAILED")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "UserIncidentSyncWorker: Failed to sync draft ${draft.localId}")
            userIncidentDao.updateSyncStatus(draft.localId, "FAILED")
            false
        }
    }

    companion object {
        private const val WORK_NAME = "user_incident_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<UserIncidentSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("USER_INCIDENT_SYNC")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.local.entity.DraftSyncState
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.repository.MediaRepository
import com.kavach.app.utils.ApiResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File
import android.net.Uri

@HiltWorker
class IncidentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val db: KavachDatabase,
    private val api: KavachApiV2,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("IncidentSyncWorker: Starting sync")
        val pendingDrafts = db.incidentDao().getPendingSyncDrafts()
        
        if (pendingDrafts.isEmpty()) {
            Timber.d("IncidentSyncWorker: No pending drafts found")
            return Result.success()
        }

        var hasFailures = false

        for (draft in pendingDrafts) {
            try {
                Timber.d("IncidentSyncWorker: Syncing draft ${draft.localId}")
                
                // 1. Upload evidence items first
                val evidence = db.incidentDao().getEvidenceForDraft(draft.localId)
                val serverUrls = mutableMapOf<String, String>()
                
                var mediaUploadSuccess = true
                for (item in evidence) {
                    if (item.serverUrl != null) {
                        serverUrls["primary_image"] = item.serverUrl
                        continue
                    }

                    val file = File(item.filePath)
                    if (!file.exists()) {
                        Timber.e("IncidentSyncWorker: Evidence file missing at ${item.filePath}")
                        continue
                    }

                    val uploadResult = mediaRepository.uploadEvidence(
                        title = "Evidence for ${draft.title}",
                        imageUri = Uri.fromFile(file)
                    )

                    if (uploadResult is ApiResult.Success) {
                        val uploadedUrl = uploadResult.data.fileUrl
                        db.incidentDao().updateEvidence(item.copy(
                            serverUrl = uploadedUrl,
                            status = "COMPLETED"
                        ))
                        serverUrls["primary_image"] = uploadedUrl
                    } else {
                        Timber.e("IncidentSyncWorker: Media upload failed for ${draft.localId}")
                        mediaUploadSuccess = false
                        break
                    }
                }

                if (!mediaUploadSuccess) {
                    hasFailures = true
                    continue
                }

                // 2. Create Incident on Server
                val payload = mutableMapOf<String, Any>(
                    "title" to draft.title,
                    "summary" to draft.summary,
                    "severity" to draft.severity,
                    "type" to draft.type,
                    "occurred_at" to java.time.Instant.ofEpochMilli(draft.occurredAt).toString()
                )
                
                if (serverUrls.isNotEmpty()) {
                    payload["evidence_manifest"] = serverUrls
                }

                val response = api.createIncident(payload)
                if (response.status == "success") {
                    db.incidentDao().updateSyncStatus(
                        localId = draft.localId,
                        state = DraftSyncState.SYNCED,
                        serverId = response.data?.id
                    )
                    
                    // Cleanup: Delete local evidence files after success
                    for (item in evidence) {
                        try {
                            File(item.filePath).delete()
                        } catch (e: Exception) {
                            Timber.w("Failed to delete synced evidence file: ${item.filePath}")
                        }
                    }
                    
                    Timber.d("IncidentSyncWorker: Successfully synced ${draft.localId}")
                } else {
                    Timber.e("IncidentSyncWorker: Server rejected incident ${draft.localId}: ${response.message}")
                    hasFailures = true
                }

            } catch (e: Exception) {
                Timber.e(e, "IncidentSyncWorker: Unexpected error syncing ${draft.localId}")
                hasFailures = true
            }
        }

        return if (hasFailures) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "incident_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<IncidentSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .addTag("INCIDENT_SYNC")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}

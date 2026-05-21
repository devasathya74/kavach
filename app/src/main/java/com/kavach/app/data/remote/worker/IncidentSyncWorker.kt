package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.db.KavachDatabase
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
        Timber.d("IncidentSyncWorker: Starting sync loop")
        val pendingIncidents = db.incidentDao().getIncidentsByStatus("PENDING_SYNC")
        
        if (pendingIncidents.isEmpty()) {
            Timber.d("IncidentSyncWorker: No pending incidents")
            return Result.success()
        }

        var hasFailures = false

        for (incident in pendingIncidents) {
            try {
                // 1. Mark as Syncing
                db.incidentDao().upsertIncident(incident.copy(syncStatus = "SYNCING"))
                
                // 2. Upload attachments
                val attachments = db.incidentDao().getAttachmentsForIncident(incident.localId)
                var attachmentsSuccess = true
                
                for (attachment in attachments) {
                    if (attachment.status == "COMPLETED") continue
                    
                    val uri = attachment.localUri?.let { Uri.parse(it) } ?: continue
                    val result = mediaRepository.uploadEvidence(
                        title = "Attachment for ${incident.title}",
                        imageUri = uri
                    )
                    
                    if (result is ApiResult.Success) {
                        db.incidentDao().updateAttachment(attachment.copy(
                            status = "COMPLETED",
                            remoteUrl = result.data.fileUrl,
                            uploadProgress = 100
                        ))
                    } else {
                        Timber.e("IncidentSyncWorker: Attachment upload failed for ${incident.localId}")
                        attachmentsSuccess = false
                        break
                    }
                }

                if (!attachmentsSuccess) {
                    db.incidentDao().upsertIncident(incident.copy(syncStatus = "PENDING_SYNC")) // Fallback to retry
                    hasFailures = true
                    continue
                }

                // 3. Submit Incident
                val request = com.kavach.app.data.remote.dto.v2.CreateIncidentRequest(
                    title = incident.title,
                    summary = incident.summary,
                    severity = incident.severity,
                    type = incident.type,
                    occurredAt = java.time.Instant.ofEpochMilli(incident.occurredAt).toString()
                )

                val response = api.createIncident(request)
                if (response.isSuccessful && response.body()?.status == "success") {
                    val serverId = response.body()?.data?.id ?: ""
                    db.incidentDao().markAsSynced(incident.localId, serverId)
                    Timber.d("IncidentSyncWorker: Successfully synced incident ${incident.localId} -> $serverId")
                } else {
                    Timber.e("IncidentSyncWorker: Server rejected incident ${incident.localId}")
                    db.incidentDao().upsertIncident(incident.copy(syncStatus = "FAILED"))
                    hasFailures = true
                }

            } catch (e: Exception) {
                Timber.e(e, "IncidentSyncWorker: Critical error during sync")
                db.incidentDao().upsertIncident(incident.copy(syncStatus = "PENDING_SYNC"))
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
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
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

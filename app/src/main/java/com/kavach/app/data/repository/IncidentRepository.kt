package com.kavach.app.data.repository

import android.content.Context
import com.kavach.app.data.local.dao.IncidentDao
import com.kavach.app.data.local.entity.IncidentAttachmentEntity
import com.kavach.app.data.local.entity.IncidentEntity
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.worker.IncidentSyncWorker
import com.kavach.app.domain.model.Incident
import com.kavach.app.domain.model.IncidentAttachment
import com.kavach.app.domain.model.IncidentStatus
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val incidentDao: IncidentDao,
    private val api: KavachApiV2
) {

    fun observeIncidents(): Flow<List<Incident>> =
        incidentDao.observeAllIncidents().map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun observeIncidentAttachments(localId: String): Flow<List<IncidentAttachment>> =
        incidentDao.observeAttachments(localId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun createIncidentDraft(
        title: String = "",
        summary: String = "",
        type: String = "OTHER",
        severity: String = "LOW",
        latitude: Double? = null,
        longitude: Double? = null
    ): String {
        val localId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        val sessionToken = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = IncidentEntity(
            localId = localId,
            correlationId = correlationId,
            sessionToken = sessionToken,
            title = title,
            summary = summary,
            type = type,
            severity = severity,
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
            latitude = latitude,
            longitude = longitude,
            syncStatus = "DRAFT",
            isDirty = true
        )

        incidentDao.upsertIncident(entity)
        return localId
    }

    suspend fun getDraftById(localId: String): Incident? {
        return incidentDao.getIncidentById(localId)?.toDomainModel()
    }

    suspend fun updateDraft(
        localId: String,
        sessionToken: String,
        title: String? = null,
        summary: String? = null,
        type: String? = null,
        severity: String? = null
    ) {
        val existing = incidentDao.getIncidentById(localId) ?: return
        
        // Conflict Recovery: Session Token check
        if (existing.sessionToken != sessionToken) {
            timber.log.Timber.w("Draft update rejected: Session token mismatch. Draft may have been recovered elsewhere.")
            return
        }

        val updated = existing.copy(
            title = title ?: existing.title,
            summary = summary ?: existing.summary,
            type = type ?: existing.type,
            severity = severity ?: existing.severity,
            updatedAt = System.currentTimeMillis(),
            isDirty = true
        )
        incidentDao.upsertIncident(updated)
    }

    suspend fun addAttachmentToDraft(
        incidentId: String,
        uri: String,
        mediaType: String,
        mimeType: String,
        fileSize: Long
    ) {
        val attachment = IncidentAttachmentEntity(
            localId = UUID.randomUUID().toString(),
            incidentLocalId = incidentId,
            localUri = uri,
            mediaType = mediaType,
            mimeType = mimeType,
            fileSize = fileSize,
            status = "LOCAL_ONLY"
        )
        incidentDao.insertAttachment(attachment)
    }

    suspend fun submitIncident(localId: String) {
        val incident = incidentDao.getIncidentByLocalId(localId) ?: return
        
        // Queue all attachments for upload
        val attachments = incidentDao.getAttachmentsForIncident(localId)
        attachments.forEach { 
            if (it.status == "LOCAL_ONLY" || it.status == "FAILED") {
                incidentDao.updateAttachmentStatus(it.localId, "QUEUED", 0)
            }
        }
        
        incidentDao.upsertIncident(incident.copy(syncStatus = "PENDING_UPLOAD"))
        
        // Schedule specialized workers
        com.kavach.app.data.remote.worker.IncidentMediaUploadWorker.schedule(context)
        IncidentSyncWorker.schedule(context)
    }

    private fun IncidentEntity.toDomainModel(): Incident {
        return Incident(
            localId = localId,
            serverId = serverId,
            correlationId = correlationId,
            title = title,
            summary = summary,
            type = type,
            severity = severity,
            status = when (syncStatus) {
                "DRAFT" -> IncidentStatus.Draft
                "PENDING_UPLOAD" -> IncidentStatus.PendingSync
                "SYNCING" -> IncidentStatus.Syncing
                "ACTIVE" -> IncidentStatus.Active
                "RESOLVED" -> IncidentStatus.Resolved
                "FAILED" -> IncidentStatus.Failed
                else -> IncidentStatus.Conflicted
            },
            occurredAt = occurredAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun IncidentAttachmentEntity.toDomainModel(): IncidentAttachment {
        return IncidentAttachment(
            localId = localId,
            remoteUrl = remoteUrl,
            localUri = localUri,
            mediaType = mediaType,
            uploadProgress = uploadProgress,
            isUploaded = status == "UPLOADED"
        )
    }

    suspend fun reconcileFromServer(serverId: String): ApiResult<Unit> = safeApiCall {
        val response = api.getIncidents()
        val dto = response.results.find { it.id == serverId }
        if (dto != null) {
            val entity = IncidentEntity(
                localId = UUID.randomUUID().toString(),
                serverId = dto.id,
                correlationId = UUID.randomUUID().toString(),
                sessionToken = UUID.randomUUID().toString(),
                title = dto.title,
                summary = dto.description ?: "",
                type = "OTHER",
                severity = dto.severity,
                occurredAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                latitude = null,
                longitude = null,
                syncStatus = dto.status,
                isDirty = false
            )
            incidentDao.reconcileIncident(entity)
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Incident not found on server")
        }
    }
}

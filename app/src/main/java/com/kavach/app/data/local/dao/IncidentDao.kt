package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.IncidentAttachmentEntity
import com.kavach.app.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Query("SELECT * FROM incidents ORDER BY operationalTimestamp DESC")
    fun observeAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE syncStatus = 'DRAFT'")
    fun getActiveDrafts(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE localId = :id")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Query("DELETE FROM incident_attachments WHERE status = 'ORPHANED' OR (status = 'LOCAL_ONLY' AND createdAt < :timestamp)")
    suspend fun cleanupOrphanAttachments(timestamp: Long)

    @Query("UPDATE incident_attachments SET status = :status, uploadProgress = :progress WHERE localId = :attachmentId")
    suspend fun updateAttachmentStatus(attachmentId: String, status: String, progress: Int)

    @Query("SELECT * FROM incident_attachments WHERE status = 'QUEUED' LIMIT 1")
    suspend fun getNextQueuedAttachment(): IncidentAttachmentEntity?

    @Query("SELECT * FROM incidents WHERE syncStatus = :status")
    suspend fun getIncidentsByStatus(status: String): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE localId = :localId")
    suspend fun getIncidentByLocalId(localId: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE serverId = :serverId")
    suspend fun getIncidentByServerId(serverId: String): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIncident(incident: IncidentEntity)

    @Transaction
    suspend fun reconcileIncident(incident: IncidentEntity) {
        val existing = getIncidentByServerId(incident.serverId ?: "")
            ?: getIncidentByLocalId(incident.localId)
            
        if (existing == null) {
            upsertIncident(incident)
        } else {
            // Version control: Only update if server is newer or local is not dirty
            if (!existing.isDirty || (incident.serverUpdatedAt ?: 0) > (existing.serverUpdatedAt ?: 0)) {
                upsertIncident(incident.copy(localId = existing.localId))
            }
        }
    }

    @Query("UPDATE incidents SET syncStatus = :status, serverId = :serverId, isDirty = 0 WHERE localId = :localId")
    suspend fun markAsSynced(localId: String, serverId: String, status: String = "ACTIVE")

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: IncidentAttachmentEntity)

    @Query("SELECT * FROM incident_attachments WHERE incidentLocalId = :incidentLocalId")
    fun observeAttachments(incidentLocalId: String): Flow<List<IncidentAttachmentEntity>>

    @Query("SELECT * FROM incident_attachments WHERE incidentLocalId = :incidentLocalId")
    suspend fun getAttachmentsForIncident(incidentLocalId: String): List<IncidentAttachmentEntity>

    @Update
    suspend fun updateAttachment(attachment: IncidentAttachmentEntity)

    @Query("DELETE FROM incidents WHERE localId = :localId")
    suspend fun deleteIncident(localId: String)

    @Query("SELECT * FROM incident_attachments")
    suspend fun getAllAttachments(): List<IncidentAttachmentEntity>
}

package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.SosEntity
import com.kavach.app.data.local.entity.UserIncidentAttachmentEntity
import com.kavach.app.data.local.entity.UserIncidentDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SosDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSos(sos: SosEntity)

    @Query("SELECT * FROM sos_queue WHERE status = 'QUEUED' OR status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getPendingSos(): List<SosEntity>

    @Query("SELECT * FROM sos_queue ORDER BY createdAt DESC LIMIT 20")
    fun observeRecentSos(): Flow<List<SosEntity>>

    @Query("UPDATE sos_queue SET status = :status, lastAttemptAt = :time, retryCount = retryCount + 1, errorMessage = :error WHERE localId = :localId")
    suspend fun updateStatus(localId: String, status: String, time: Long = System.currentTimeMillis(), error: String? = null)

    @Query("UPDATE sos_queue SET status = 'SENT', serverAckAt = :ackAt WHERE localId = :localId")
    suspend fun markAsSent(localId: String, ackAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sos_queue WHERE status = 'QUEUED' OR status = 'TRANSMITTING'")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface UserIncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: UserIncidentDraftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: UserIncidentAttachmentEntity)

    @Query("SELECT * FROM user_incident_drafts WHERE localId = :localId")
    suspend fun getDraft(localId: String): UserIncidentDraftEntity?

    @Query("SELECT * FROM user_incident_drafts ORDER BY createdAt DESC")
    fun observeAllDrafts(): Flow<List<UserIncidentDraftEntity>>

    @Query("SELECT * FROM user_incident_drafts WHERE syncStatus IN ('DRAFT', 'QUEUED', 'FAILED') ORDER BY createdAt DESC")
    fun observePendingDrafts(): Flow<List<UserIncidentDraftEntity>>

    @Query("SELECT * FROM user_incident_drafts WHERE syncStatus IN ('QUEUED', 'FAILED') ORDER BY createdAt DESC")
    suspend fun getPendingDraftsList(): List<UserIncidentDraftEntity>

    @Query("SELECT * FROM user_incident_attachments WHERE incidentLocalId = :incidentLocalId")
    suspend fun getAttachments(incidentLocalId: String): List<UserIncidentAttachmentEntity>

    @Query("UPDATE user_incident_drafts SET syncStatus = :status, retryCount = retryCount + 1 WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: String, status: String)

    @Query("UPDATE user_incident_drafts SET syncStatus = 'SYNCED', serverId = :serverId WHERE localId = :localId")
    suspend fun markSynced(localId: String, serverId: String)

    @Query("UPDATE user_incident_attachments SET uploadStatus = :status, remoteUrl = :url WHERE localId = :attachmentId")
    suspend fun updateAttachmentStatus(attachmentId: String, status: String, url: String? = null)

    // Pending uploads count — for sync status display
    @Query("SELECT COUNT(*) FROM user_incident_drafts WHERE syncStatus IN ('QUEUED', 'UPLOADING')")
    fun observePendingUploadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_incident_attachments WHERE uploadStatus = 'PENDING'")
    fun observePendingAttachmentCount(): Flow<Int>
}

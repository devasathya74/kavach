package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.EvidenceUploadEntity
import com.kavach.app.data.local.entity.IncidentDraftEntity
import com.kavach.app.data.local.entity.DraftSyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Query("SELECT * FROM incident_drafts ORDER BY createdAt DESC")
    fun getAllDrafts(): Flow<List<IncidentDraftEntity>>

    @Query("SELECT * FROM incident_drafts WHERE syncState = :state")
    suspend fun getDraftsBySyncState(state: DraftSyncState): List<IncidentDraftEntity>

    @Query("SELECT * FROM incident_drafts WHERE syncState = 'PENDING_SYNC'")
    suspend fun getPendingSyncDrafts(): List<IncidentDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: IncidentDraftEntity)

    @Update
    suspend fun updateDraft(draft: IncidentDraftEntity)

    @Query("UPDATE incident_drafts SET syncState = :state, serverId = :serverId WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: String, state: DraftSyncState, serverId: String? = null)

    @Query("SELECT * FROM evidence_upload_queue WHERE incidentLocalId = :incidentLocalId")
    suspend fun getEvidenceForDraft(incidentLocalId: String): List<EvidenceUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceUploadEntity)

    @Update
    suspend fun updateEvidence(evidence: EvidenceUploadEntity)

    @Query("DELETE FROM incident_drafts WHERE localId = :localId")
    suspend fun deleteDraft(localId: String)

    @Query("DELETE FROM evidence_upload_queue WHERE incidentLocalId = :localId")
    suspend fun deleteEvidenceForDraft(localId: String)
}

package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DraftSyncState {
    DRAFT, PENDING_SYNC, SYNCED, FAILED, CONFLICT
}

@Entity(tableName = "incident_drafts")
data class IncidentDraftEntity(
    @PrimaryKey val localId: String,
    val type: String,
    val title: String,
    val summary: String,
    val severity: String,
    val latitude: Double?,
    val longitude: Double?,
    val occurredAt: Long,
    
    val syncState: DraftSyncState = DraftSyncState.DRAFT,
    val serverId: String? = null,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evidence_upload_queue")
data class EvidenceUploadEntity(
    @PrimaryKey val localId: String,
    val incidentLocalId: String,
    val filePath: String,
    val mediaType: String, // IMAGE, VIDEO
    
    val uploadProgress: Int = 0,
    val status: String = "PENDING", // PENDING, UPLOADING, COMPLETED, FAILED
    val serverUrl: String? = null
)

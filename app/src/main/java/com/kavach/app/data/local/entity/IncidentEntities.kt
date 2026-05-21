package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * IncidentEntity — The unified storage for both local drafts and remote incidents.
 * Supports reconciliation, offline edits, and sync state tracking.
 */
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val localId: String,          // UUID for local creation
    val serverId: String? = null,             // ID from backend
    val correlationId: String,                // Unique ID for deduplication across transport
    val sessionToken: String = "",            // Prevents stale draft restore conflicts
    
    val title: String,
    val summary: String,
    val type: String,
    val severity: String,
    val occurredAt: Long,
    val operationalTimestamp: Long = occurredAt, 
    val syncTimestamp: Long? = null,             
    val createdAt: Long,
    val updatedAt: Long,
    
    val latitude: Double?,
    val longitude: Double?,
    
    // Sync & Reconciliation State
    val syncStatus: String,                   // DRAFT, PENDING_UPLOAD, UPLOADING, SYNCING, ACTIVE, RESOLVED, FAILED
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val serverUpdatedAt: Long? = null,         
    
    val version: Int = 1,                      
    val sequence: Long = 0,                    
    val lastRemoteEventId: String? = null,     
    
    val isDirty: Boolean = false              
)

/**
 * IncidentAttachmentEntity — Tracks media related to an incident with a hardened lifecycle.
 */
@Entity(
    tableName = "incident_attachments",
    indices = [Index(value = ["incidentLocalId"])]
)
data class IncidentAttachmentEntity(
    @PrimaryKey val localId: String,
    val incidentLocalId: String,
    val remoteUrl: String? = null,
    val localUri: String? = null,             
    val mediaType: String,                   // IMAGE, VIDEO
    val mimeType: String = "",
    val fileSize: Long = 0,
    val checksum: String? = null,            // Integrity verification
    
    val status: String = "LOCAL_ONLY",        // LOCAL_ONLY, QUEUED, UPLOADING, UPLOADED, FAILED, ORPHANED
    val uploadProgress: Int = 0,
    val uploadAttempt: Int = 0,
    val lastFailureReason: String? = null,
    val isMandatory: Boolean = false,
    val attachmentVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * SosEntity — Persistent SOS signal queue.
 *
 * PIPELINE:
 *   UI → DB commit (instant) → SosWorker (WorkManager) → WebSocket priority emit → REST fallback
 *
 * CRITICAL RULES:
 * 1. DB commit happens BEFORE any network attempt (process-death safe)
 * 2. WorkManager ensures retry on network loss
 * 3. Status tracks each pipeline stage
 * 4. correlationId prevents duplicate server submissions
 */
@Entity(
    tableName = "sos_queue",
    indices = [Index(value = ["correlationId"], unique = true)]
)
data class SosEntity(
    @PrimaryKey val localId: String,
    val correlationId: String,        // Dedup key for server
    val senderPno: String,
    val senderUnit: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val message: String = "SOS — IMMEDIATE ASSISTANCE REQUIRED",
    val status: String = "QUEUED",    // QUEUED, TRANSMITTING, SENT, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val serverAckAt: Long? = null     // Server confirmation timestamp
)

/**
 * UserIncidentDraftEntity — Field incident report draft.
 *
 * User creates → photos attached → offline queue → sync on reconnect.
 * Simple. Photo + description + type. No operational intelligence.
 */
@Entity(
    tableName = "user_incident_drafts",
    indices = [Index(value = ["correlationId"], unique = true)]
)
data class UserIncidentDraftEntity(
    @PrimaryKey val localId: String,
    val correlationId: String,
    val reporterPno: String,
    val reporterUnit: String,
    val title: String,
    val description: String,
    val type: String = "FIELD_REPORT",   // FIELD_REPORT, EQUIPMENT_ISSUE, PERSONNEL_INCIDENT, EMERGENCY
    val severity: String = "MEDIUM",     // LOW, MEDIUM, HIGH, CRITICAL
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "DRAFT",    // DRAFT, QUEUED, UPLOADING, SYNCED, FAILED
    val retryCount: Int = 0,
    val serverId: String? = null
)

/**
 * UserIncidentAttachmentEntity — Photo attachments for field reports.
 *
 * RULE: Only private storage absolute paths. Never content:// URIs.
 * Copied immediately on selection via BroadcastFileManager (reused).
 */
@Entity(
    tableName = "user_incident_attachments",
    indices = [Index(value = ["incidentLocalId"])]
)
data class UserIncidentAttachmentEntity(
    @PrimaryKey val localId: String,
    val incidentLocalId: String,
    val localPath: String,          // App-private absolute path
    val mimeType: String,
    val checksum: String? = null,
    val uploadStatus: String = "PENDING", // PENDING, UPLOADING, UPLOADED, FAILED
    val remoteUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded

/**
 * BroadcastEntity — Canonical message authoritative data.
 */
@Entity(
    tableName = "broadcasts",
    indices = [Index(value = ["serverId"], unique = true)]
)
data class BroadcastEntity(
    @PrimaryKey val localId: String,
    val serverId: String? = null,
    val correlationId: String? = null,
    val title: String,
    val content: String,
    val senderId: String,
    val senderName: String,
    val type: String,               // EMERGENCY, ALERT, GENERAL
    val priority: String,           // HIGH, NORMAL, LOW
    val status: String,             // DRAFT, QUEUED, DISTRIBUTING, DELIVERED, EXPIRED
    val createdAt: Long,            // Server timestamp
    val receivedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val supersedesBroadcastId: String? = null,
    val version: Int = 1,
    val sequence: Long = 0
)

data class BroadcastWithStats(
    @Embedded val broadcast: BroadcastEntity,
    val totalRecipients: Int,
    val deliveredCount: Int,
    val readCount: Int,
    val ackCount: Int,
    val failedCount: Int
)

/**
 * BroadcastDeliveryEntity — Recipient-specific state tracking.
 * Isolation Layer: Prevents "Broadcast Storm" data corruption.
 */
@Entity(
    tableName = "broadcast_deliveries",
    indices = [Index(value = ["broadcastId", "recipientId"], unique = true)]
)
data class BroadcastDeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val broadcastId: String,        // Maps to serverId or localId
    val recipientId: String,
    val status: String,             // PENDING, SENT, RECEIVED, READ, ACKNOWLEDGED, FAILED
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val acknowledgedAt: Long? = null
)

/**
 * BroadcastAttachmentEntity — Media references for broadcasts.
 */
@Entity(tableName = "broadcast_attachments")
data class BroadcastAttachmentEntity(
    @PrimaryKey val localId: String,
    val broadcastLocalId: String,
    val uri: String,
    val mimeType: String,
    val checksum: String? = null,
    val uploadStatus: String = "PENDING", // PENDING, UPLOADING, UPLOADED, FAILED
    val remoteUrl: String? = null
)

/**
 * BroadcastDraftEntity — Process-death resilient draft persistence.
 */
@Entity(tableName = "broadcast_drafts")
data class BroadcastDraftEntity(
    @PrimaryKey val draftId: String,
    val title: String,
    val content: String,
    val priority: String,
    val type: String,
    val selectedUserIdsJson: String, // JSON array of canonical User IDs
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * BroadcastDispatchQueueEntity — Queue state for transactional dispatch.
 */
@Entity(
    tableName = "broadcast_dispatch_queue",
    indices = [Index(value = ["correlationId"], unique = true)]
)
data class BroadcastDispatchQueueEntity(
    @PrimaryKey val dispatchId: String,
    val draftId: String,
    val correlationId: String,
    val status: String, // QUEUED, UPLOADING, READY_FOR_DISPATCH, DISPATCHING, COMPLETED, FAILED
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)

/**
 * BroadcastMutationQueue — Queue for outgoing delivery acknowledgments.
 */
@Entity(tableName = "broadcast_mutation_queue")
data class BroadcastMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val broadcastId: String,
    val actionType: String,         // READ, ACKNOWLEDGE
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

package com.kavach.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class SyncState {
    SYNCED, STALE, DIRTY, FAILED, CONFLICT
}

@Entity(
    tableName = "officer_cache",
    indices = [
        Index(value = ["unitCode"]),
        Index(value = ["unitCode", "searchableName"]),  // composite: company dropdown query
        Index(value = ["searchableName"]),
        Index(value = ["searchablePno"])
    ]
)
data class OfficerCacheEntity(
    @PrimaryKey val id: String,
    val pno: String,
    val role: String,
    val unitCode: String,
    val unitName: String,
    val isActive: Boolean = true,
    val searchableName: String = "",
    val searchablePno: String = "",
    val version: Int = 1,

    // Conflict Detection & Consistency
    val revision: Long = 1,
    val syncedRevision: Long = 1,
    val isDirty: Boolean = false,

    val lastSyncedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.SYNCED,
    val etag: String? = null
)

@Entity(tableName = "officer_profile_cache")
data class OfficerProfileCacheEntity(
    @PrimaryKey val officerId: String,
    val name: String,
    val rankCode: String,
    val rankName: String,
    val companyName: String?,
    val platoonNumber: Int?,
    val phone: String,
    val email: String?,
    val serviceStatus: String,
    val imageUrl: String?,
    
    // Conflict Detection & Consistency
    val revision: Long = 1,
    val syncedRevision: Long = 1,
    val isDirty: Boolean = false,
    
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.SYNCED
)

@Entity(tableName = "officer_device_cache")
data class OfficerDeviceCacheEntity(
    @PrimaryKey val id: String,
    val officerId: String,
    val deviceId: String,
    val deviceName: String,
    val status: String,
    val trustScore: Float,
    val integrityLevel: String,
    val lastActive: String?,
    val lastHeartbeatAt: Long? = null,
    
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.SYNCED
)

/**
 * BulkMutationEntity — Governed batch operation tracking.
 * Prevents "Mutation Storms" and ensures auditability.
 */
@Entity(tableName = "bulk_mutations")
data class BulkMutationEntity(
    @PrimaryKey val correlationId: String,
    val actionType: String,             // BLOCK, UNBLOCK, DELETE
    val targetIds: String,               // Comma separated canonical IDs
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "QUEUED",       // QUEUED, PROCESSING, COMPLETED, FAILED
    val filterContext: String? = null,   // Snapshot of filters when action was taken
    val retryCount: Int = 0
)

data class OfficerWithProfile(
    @Embedded
    val officer: OfficerCacheEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "officerId"
    )
    val profile: OfficerProfileCacheEntity?
)

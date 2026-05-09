package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncState {
    SYNCED, STALE, DIRTY, FAILED, CONFLICT
}

@Entity(tableName = "officer_cache")
data class OfficerCacheEntity(
    @PrimaryKey val id: String,
    val pno: String,
    val role: String,
    val unitCode: String,
    val unitName: String,
    val isActive: Boolean,
    
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
    
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val syncState: SyncState = SyncState.SYNCED
)

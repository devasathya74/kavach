package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.OfficerCacheEntity
import com.kavach.app.data.local.entity.OfficerDeviceCacheEntity
import com.kavach.app.data.local.entity.OfficerProfileCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfficer(officer: OfficerCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: OfficerProfileCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<OfficerDeviceCacheEntity>)

    @Transaction
    suspend fun syncOfficer(
        officer: OfficerCacheEntity,
        profile: OfficerProfileCacheEntity,
        devices: List<OfficerDeviceCacheEntity>
    ) {
        insertOfficer(officer)
        insertProfile(profile)
        // Clear old devices for this officer and insert new ones
        deleteDevicesForOfficer(officer.id)
        insertDevices(devices)
    }

    @Query("""
        SELECT * FROM officer_cache 
        WHERE (:unitType IS NULL OR unitCode = :unitType)
        AND (:search IS NULL OR pno LIKE '%' || :search || '%')
    """)
    fun getFilteredOfficers(unitType: String?, search: String?): Flow<List<OfficerCacheEntity>>

    @Query("SELECT * FROM officer_cache")
    fun getAllOfficers(): Flow<List<OfficerCacheEntity>>

    @Query("SELECT * FROM officer_cache WHERE id = :id")
    fun getOfficerById(id: String): Flow<OfficerCacheEntity?>

    @Query("SELECT * FROM officer_profile_cache WHERE officerId = :officerId")
    fun getProfileByOfficerId(officerId: String): Flow<OfficerProfileCacheEntity?>

    @Query("SELECT * FROM officer_device_cache WHERE officerId = :officerId")
    fun getDevicesByOfficerId(officerId: String): Flow<List<OfficerDeviceCacheEntity>>

    @Query("DELETE FROM officer_device_cache WHERE officerId = :officerId")
    suspend fun deleteDevicesForOfficer(officerId: String)
}

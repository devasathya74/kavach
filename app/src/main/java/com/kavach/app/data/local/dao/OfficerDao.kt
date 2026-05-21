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

    @Transaction
    @Query("""
        SELECT * FROM officer_cache 
        WHERE (:unitType IS NULL OR unitCode = :unitType)
        AND (:search IS NULL OR searchablePno LIKE '%' || :search || '%' OR searchableName LIKE '%' || :search || '%')
    """)
    fun getFilteredOfficers(unitType: String?, search: String?): Flow<List<com.kavach.app.data.local.entity.OfficerWithProfile>>

    // ── Selection Engine ──────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM officer_cache WHERE unitCode = :unitId ORDER BY searchableName ASC")
    fun observePersonnelByUnit(unitId: String): Flow<List<com.kavach.app.data.local.entity.OfficerWithProfile>>

    @Transaction
    @Query("""
        SELECT o.* FROM officer_cache o 
        INNER JOIN officer_profile_cache p ON o.id = p.officerId 
        WHERE p.companyName = :companyId 
        ORDER BY o.searchableName ASC
    """)
    fun observePersonnelByCompany(companyId: String): Flow<List<com.kavach.app.data.local.entity.OfficerWithProfile>>

    @Transaction
    @Query("""
        SELECT * FROM officer_cache 
        WHERE searchablePno LIKE '%' || :query || '%' 
           OR searchableName LIKE '%' || :query || '%'
        ORDER BY searchableName ASC
    """)
    fun searchPersonnel(query: String): Flow<List<com.kavach.app.data.local.entity.OfficerWithProfile>>

    @Query("SELECT * FROM bulk_mutations ORDER BY createdAt DESC")
    fun observeBulkMutations(): Flow<List<com.kavach.app.data.local.entity.BulkMutationEntity>>

    @Query("UPDATE officer_cache SET searchableName = :name, searchablePno = :pno WHERE id = :id")
    suspend fun updateSearchIndexes(id: String, name: String, pno: String)

    // ── Bulk Mutations ────────────────────────────────────────
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBulkMutation(mutation: com.kavach.app.data.local.entity.BulkMutationEntity)

    @Query("SELECT * FROM bulk_mutations WHERE status = 'QUEUED'")
    suspend fun getPendingBulkMutations(): List<com.kavach.app.data.local.entity.BulkMutationEntity>

    @Query("UPDATE bulk_mutations SET status = :status WHERE correlationId = :id")
    suspend fun updateBulkMutationStatus(id: String, status: String)

    @Query("DELETE FROM bulk_mutations WHERE correlationId = :id")
    suspend fun deleteBulkMutation(id: String)

    @Query("SELECT * FROM officer_cache")
    fun getAllOfficers(): Flow<List<OfficerCacheEntity>>

    @Query("SELECT * FROM officer_cache WHERE id = :id")
    fun getOfficerById(id: String): Flow<OfficerCacheEntity?>

    @Query("SELECT * FROM officer_profile_cache WHERE officerId = :officerId")
    fun getProfileByOfficerId(officerId: String): Flow<OfficerProfileCacheEntity?>

    @Query("SELECT * FROM officer_device_cache WHERE officerId = :officerId")
    fun getDevicesByOfficerId(officerId: String): Flow<List<OfficerDeviceCacheEntity>>

    @Query("""
        SELECT * FROM officer_device_cache 
        WHERE (:search IS NULL OR deviceId LIKE '%' || :search || '%' OR deviceName LIKE '%' || :search || '%')
        AND (:status IS NULL OR status = :status)
    """)
    fun observeAllDevices(search: String?, status: String?): Flow<List<OfficerDeviceCacheEntity>>

    @Query("DELETE FROM officer_device_cache WHERE officerId = :officerId")
    suspend fun deleteDevicesForOfficer(officerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonnelAction(action: com.kavach.app.data.local.entity.PersonnelActionEntity)

    @Query("DELETE FROM personnel_mutation_queue WHERE officerId = :officerId")
    suspend fun deletePersonnelAction(officerId: String)

    @Query("SELECT * FROM personnel_mutation_queue")
    suspend fun getPendingPersonnelActions(): List<com.kavach.app.data.local.entity.PersonnelActionEntity>

    // ── Aggregation Queries ───────────────────────────────────
    @Query("SELECT COUNT(*) FROM officer_cache")
    fun getOfficerCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM officer_device_cache")
    fun getDeviceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM officer_device_cache WHERE status = 'ACTIVE'")
    fun getActiveDeviceCount(): Flow<Int>
}

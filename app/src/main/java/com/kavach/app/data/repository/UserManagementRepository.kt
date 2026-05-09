package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.OfficerDao
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.personnel.OfficerDto
import com.kavach.app.data.remote.dto.system.DraftChangeDto
import com.kavach.app.data.remote.dto.v2.OfficerDeviceDto
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserManagementRepository — Single Source of Truth for Personnel Management.
 *
 * Architecture:
 *   - Observe: Room DAO (offline-first, real-time Flow)
 *   - Sync: KavachApiV2 (pull from server, write to Room)
 *   - Mutations: KavachApiV2 (write to server, refresh from server)
 *
 * All data exposed via ApiResult<T> for consistent error handling.
 */
@Singleton
class UserManagementRepository @Inject constructor(
    private val api: KavachApiV2,
    private val officerDao: OfficerDao
) {

    // ── Observe (Room / Offline-First) ────────────────────────────────────────

    /**
     * Observe officer list from Room with optional filters.
     * Returns immediately from cache; call refreshUsers() to sync from server.
     */
    fun observeUsers(
        unitType: String? = null,
        search: String? = null
    ): Flow<List<OfficerDto>> =
        officerDao.getFilteredOfficers(unitType, search).map { entities ->
            entities.map { entity ->
                OfficerDto(
                    id                  = entity.id,
                    pno                 = entity.pno,
                    role                = entity.role,
                    unit                = null,  // Loaded from cache — unit detail not stored in entity
                    isActive            = entity.isActive,
                    profile             = null,  // Profile loaded separately via officerDao.getProfileByOfficerId()
                    devices             = emptyList(),
                    mustChangePassword  = false
                )
            }
        }

    // ── Sync (Server → Room) ──────────────────────────────────────────────────

    /**
     * Pull officers from server and persist to Room.
     * Handles pagination — pass page=1 for initial load.
     */
    suspend fun refreshUsers(
        page: Int = 1,
        unitType: String? = null,
        company: String? = null,
        platoon: Int? = null,
        search: String? = null
    ): ApiResult<Unit> = safeApiCall {
        val response = api.getUsers(page, unitType, company, platoon, search)
        response.results.forEach { dto ->
            officerDao.insertOfficer(dto.toEntity())
            dto.profile?.let { profile ->
                officerDao.insertProfile(dto.toProfileEntity())
            }
        }
        ApiResult.Success(Unit)
    }

    // ── Network Reads ─────────────────────────────────────────────────────────

    /**
     * Fetch complete officer detail directly from network.
     * Use for detail screen where fresh data is critical.
     */
    suspend fun getUserDetailNetwork(officerId: String): ApiResult<OfficerDto> = safeApiCall {
        val dto = api.getUserDetail(officerId)
        ApiResult.Success(dto)
    }

    /**
     * Get list of all officers (for officer picker, etc.).
     */
    suspend fun getOfficers(
        page: Int = 1
    ): ApiResult<List<OfficerDto>> = safeApiCall {
        val response = api.getUsers(page)
        ApiResult.Success(response.results)
    }

    // ── Mutations (Server) ────────────────────────────────────────────────────

    suspend fun createUser(data: Map<String, Any>): ApiResult<OfficerDto> = safeApiCall {
        val response = api.createUser(data)
        if (response.status == "success" && response.data != null) {
            // Persist new officer to Room
            officerDao.insertOfficer(response.data.toEntity())
            ApiResult.Success(response.data)
        } else {
            ApiResult.Error(response.message ?: "User creation failed")
        }
    }

    suspend fun updateUser(id: String, data: Map<String, Any>): ApiResult<OfficerDto> = safeApiCall {
        val response = api.updateUser(id, data)
        if (response.status == "success" && response.data != null) {
            officerDao.insertOfficer(response.data.toEntity())
            ApiResult.Success(response.data)
        } else {
            ApiResult.Error(response.message ?: "Update failed")
        }
    }

    suspend fun deleteUser(id: String): ApiResult<Unit> = safeApiCall {
        api.deleteUser(id)
        ApiResult.Success(Unit)
    }

    suspend fun resetPassword(id: String, newPassword: String): ApiResult<Unit> = safeApiCall {
        val response = api.resetPassword(id, mapOf("new_password" to newPassword))
        if (response.status == "success") ApiResult.Success(Unit)
        else ApiResult.Error(response.message ?: "Password reset failed")
    }

    suspend fun deactivateUser(officerId: String, reason: String): ApiResult<Unit> = safeApiCall {
        val response = api.deactivateUser(officerId, mapOf("reason" to reason))
        if (response.status == "success") ApiResult.Success(Unit)
        else ApiResult.Error(response.message ?: "Deactivation failed")
    }

    suspend fun globalLogout(officerId: String): ApiResult<Unit> = safeApiCall {
        val response = api.globalLogout(officerId)
        if (response.status == "success") ApiResult.Success(Unit)
        else ApiResult.Error(response.message ?: "Global logout failed")
    }

    // ── Device Management ─────────────────────────────────────────────────────

    suspend fun revokeDevice(officerId: String, deviceId: String): ApiResult<Unit> = safeApiCall {
        val response = api.revokeDevice(officerId, mapOf("device_id" to deviceId))
        if (response.status == "success") ApiResult.Success(Unit)
        else ApiResult.Error(response.message ?: "Device revoke failed")
    }

    suspend fun getDevices(
        search: String? = null,
        status: String? = null
    ): ApiResult<List<OfficerDeviceDto>> = safeApiCall {
        val response = api.getDevices(search, status)
        ApiResult.Success(response.results)
    }

    // ── Governance / Approval Pipeline ───────────────────────────────────────

    suspend fun getPendingChanges(): ApiResult<List<DraftChangeDto>> = safeApiCall {
        val changes = api.getPendingChanges()
        ApiResult.Success(changes)
    }

    suspend fun approveChange(id: Int): ApiResult<Unit> = safeApiCall {
        val response = api.approveChange(id)
        if (response.status == "success") ApiResult.Success(Unit)
        else ApiResult.Error(response.message ?: "Approval failed", code = 403)
    }

    suspend fun rejectChange(id: Int): ApiResult<Unit> = safeApiCall {
        val response = api.rejectChange(id)
        if (response.status == "success") ApiResult.Success(Unit)
        else ApiResult.Error(response.message ?: "Rejection failed")
    }
}

// ── Entity Mappers ────────────────────────────────────────────────────────────

private fun OfficerDto.toEntity() = com.kavach.app.data.local.entity.OfficerCacheEntity(
    id       = id,
    pno      = pno,
    role     = role,
    isActive = isActive,
    unitCode = unit?.code ?: "",
    unitName = unit?.name ?: ""
)

private fun OfficerDto.toProfileEntity() = com.kavach.app.data.local.entity.OfficerProfileCacheEntity(
    officerId     = id,
    name          = profile?.name ?: pno,
    rankName      = profile?.rank?.name ?: "",
    rankCode      = profile?.rank?.code ?: "",
    companyName   = profile?.company?.name,
    platoonNumber = profile?.platoon?.number,
    phone         = profile?.phone ?: "",
    email         = profile?.email,
    serviceStatus = profile?.serviceStatus ?: if (isActive) "Active" else "Inactive",
    imageUrl      = profile?.image
)

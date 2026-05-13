package com.kavach.app.data.repository

import android.util.Log
import com.kavach.app.data.local.dao.OfficerDao
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.personnel.OfficerDto
import com.kavach.app.data.remote.dto.system.DraftChangeDto
import com.kavach.app.data.remote.dto.v2.*
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserManagementRepository — Single Source of Truth for Personnel Management.
 */
@Singleton
class UserManagementRepository @Inject constructor(
    private val api: KavachApiV2,
    private val officerDao: OfficerDao
) {

    suspend fun getOfficers(
        unitType: String? = null,
        search: String? = null
    ): ApiResult<List<OfficerDto>> = safeApiCall {
        val response = api.getUsers(search = search, unitType = unitType)
        ApiResult.Success(response.results)
    }

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
                    unit                = null,
                    isActive            = entity.isActive,
                    profile             = null,
                    devices             = emptyList(),
                    mustChangePassword  = false
                )
            }
        }

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

    suspend fun getUserDetailNetwork(officerId: String): ApiResult<OfficerDto> = safeApiCall {
        val dto = api.getUserDetail(officerId)
        ApiResult.Success(dto)
    }

    suspend fun createUser(request: CreateUserRequest): ApiResult<OfficerDto> = safeApiCall {
        val response = api.createUser(request)
        if (response.isSuccessful && response.body()?.status == "success") {
            val data = response.body()?.data
            if (data != null) {
                officerDao.insertOfficer(data.toEntity())
                ApiResult.Success(data)
            } else {
                ApiResult.Error("Server returned success but no data")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("CREATE_USER_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "User creation failed")
        }
    }

    suspend fun updateUser(id: String, request: UpdateUserRequest): ApiResult<OfficerDto> = safeApiCall {
        val response = api.updateUser(id, request)
        if (response.isSuccessful && response.body()?.status == "success") {
            val data = response.body()?.data
            if (data != null) {
                officerDao.insertOfficer(data.toEntity())
                ApiResult.Success(data)
            } else {
                ApiResult.Error("Update successful but no data returned")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("UPDATE_USER_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Update failed")
        }
    }

    suspend fun deleteUser(id: String): ApiResult<Unit> = safeApiCall {
        val response = api.deleteUser(id)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else {
            Log.e("DELETE_USER_FAIL", "Error: ${response.errorBody()?.string()}")
            ApiResult.Error("Deletion failed")
        }
    }

    suspend fun resetPassword(id: String, request: ResetPasswordRequest): ApiResult<Unit> = safeApiCall {
        val response = api.resetPassword(id, request)
        if (response.isSuccessful && response.body()?.status == "success") ApiResult.Success(Unit)
        else {
            val errorBody = response.errorBody()?.string()
            Log.e("RESET_PASSWORD_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Password reset failed")
        }
    }

    suspend fun deactivateUser(officerId: String, reason: String): ApiResult<Unit> = safeApiCall {
        val response = api.deactivateUser(officerId, GenericIdRequest(reason = reason))
        if (response.isSuccessful && response.body()?.status == "success") ApiResult.Success(Unit)
        else {
            val errorBody = response.errorBody()?.string()
            Log.e("DEACTIVATE_USER_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Deactivation failed")
        }
    }

    suspend fun revokeDevice(officerId: String, deviceId: String): ApiResult<Unit> = safeApiCall {
        val response = api.revokeDevice(officerId, GenericIdRequest(deviceId = deviceId))
        if (response.isSuccessful && response.body()?.status == "success") ApiResult.Success(Unit)
        else {
            val errorBody = response.errorBody()?.string()
            Log.e("REVOKE_DEVICE_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Device revoke failed")
        }
    }

    suspend fun globalLogout(officerId: String): ApiResult<Unit> = safeApiCall {
        val response = api.globalLogout(officerId)
        if (response.isSuccessful && response.body()?.status == "success") ApiResult.Success(Unit)
        else {
            val errorBody = response.errorBody()?.string()
            Log.e("GLOBAL_LOGOUT_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Global logout failed")
        }
    }

    suspend fun getDevices(
        search: String? = null,
        status: String? = null
    ): ApiResult<List<OfficerDeviceDto>> = safeApiCall {
        val response = api.getDevices(search, status)
        ApiResult.Success(response.results)
    }

    suspend fun getPendingChanges(): ApiResult<List<DraftChangeDto>> = safeApiCall {
        val changes = api.getPendingChanges()
        ApiResult.Success(changes)
    }

    suspend fun approveChange(id: String): ApiResult<Unit> = safeApiCall {
        val response = api.approveChange(id)
        if (response.isSuccessful && response.body()?.status == "success") ApiResult.Success(Unit)
        else {
            val errorBody = response.errorBody()?.string()
            Log.e("APPROVE_CHANGE_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Approval failed", code = 403)
        }
    }

    suspend fun rejectChange(id: String): ApiResult<Unit> = safeApiCall {
        val response = api.rejectChange(id)
        if (response.isSuccessful && response.body()?.status == "success") ApiResult.Success(Unit)
        else {
            val errorBody = response.errorBody()?.string()
            Log.e("REJECT_CHANGE_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Rejection failed")
        }
    }
}

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

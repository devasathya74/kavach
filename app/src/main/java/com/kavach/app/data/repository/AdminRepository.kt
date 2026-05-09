package com.kavach.app.data.repository

import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.personnel.AdminOfficerDto
import com.kavach.app.data.remote.dto.system.AdminUserActionDto
import com.kavach.app.data.remote.dto.training.SuspiciousSessionDto
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val api: KavachApiService
) {
    suspend fun getOfficers(): ApiResult<List<AdminOfficerDto>> = safeApiCall {
        val resp = api.getAdminUsers()
        if (resp.isSuccessful) {
            ApiResult.Success(resp.body()?.data ?: emptyList())
        } else {
            ApiResult.Error("Error fetching officers: ${resp.message()}", code = resp.code())
        }
    }

    suspend fun performAction(pno: String, action: String, reason: String): ApiResult<Unit> = safeApiCall {
        val resp = api.performAdminAction(AdminUserActionDto(pno, action, reason = reason))
        if (resp.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Action failed: ${resp.message()}", code = resp.code())
        }
    }

    suspend fun getSuspiciousSessions(): ApiResult<List<SuspiciousSessionDto>> = safeApiCall {
        val resp = api.getSuspiciousUsers()
        if (resp.isSuccessful) {
            ApiResult.Success(resp.body()?.data ?: emptyList())
        } else {
            ApiResult.Error("Error fetching suspicious sessions: ${resp.message()}", code = resp.code())
        }
    }

    suspend fun getLiveFeed(): ApiResult<List<com.kavach.app.data.remote.dto.system.LiveFeedEventDto>> = safeApiCall {
        val resp = api.getLiveFeed()
        if (resp.isSuccessful) {
            ApiResult.Success(resp.body()?.data ?: emptyList())
        } else {
            ApiResult.Error("Error fetching live feed: ${resp.message()}", code = resp.code())
        }
    }

    suspend fun getAnalytics(): ApiResult<com.kavach.app.data.remote.dto.system.SystemAnalyticsDto> = safeApiCall {
        val resp = api.getAnalytics()
        if (resp.isSuccessful && resp.body()?.data != null) {
            ApiResult.Success(resp.body()!!.data!!)
        } else {
            ApiResult.Error("Error fetching analytics: ${resp.message()}", code = resp.code())
        }
    }

    suspend fun getRemoteConfig(): ApiResult<Map<String, Any>> = safeApiCall {
        val resp = api.getRemoteConfig()
        if (resp.isSuccessful) {
            ApiResult.Success(resp.body()?.data ?: emptyMap())
        } else {
            ApiResult.Error("Error fetching config: ${resp.message()}", code = resp.code())
        }
    }

    suspend fun updateConfig(key: String, value: Any): ApiResult<Unit> = safeApiCall {
        val resp = api.updateRemoteConfig(mapOf("key" to key, "value" to value))
        if (resp.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Update failed: ${resp.message()}", code = resp.code())
        }
    }
}

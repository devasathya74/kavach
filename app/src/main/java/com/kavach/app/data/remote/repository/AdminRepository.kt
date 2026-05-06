package com.kavach.app.data.remote.repository

import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.AdminOfficerDto
import com.kavach.app.data.remote.dto.AdminUserActionDto
import com.kavach.app.data.remote.dto.SuspiciousSessionDto
import com.kavach.app.utils.Resource
import com.kavach.app.utils.safeCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val api: KavachApiService
) {
    suspend fun getOfficers(): Resource<List<AdminOfficerDto>> = safeCall {
        val resp = api.getAdminUsers()
        if (resp.isSuccessful) {
            Resource.Success(resp.body()?.data ?: emptyList())
        } else {
            Resource.Error("Error fetching officers: ${resp.message()}")
        }
    }

    suspend fun performAction(pno: String, action: String, reason: String): Resource<Unit> = safeCall {
        val resp = api.performAdminAction(AdminUserActionDto(pno, action, reason = reason))
        if (resp.isSuccessful) {
            Resource.Success(Unit)
        } else {
            Resource.Error("Action failed: ${resp.message()}")
        }
    }

    suspend fun getSuspiciousSessions(): Resource<List<SuspiciousSessionDto>> = safeCall {
        val resp = api.getSuspiciousUsers()
        if (resp.isSuccessful) {
            Resource.Success(resp.body()?.data ?: emptyList())
        } else {
            Resource.Error("Error fetching suspicious sessions: ${resp.message()}")
        }
    }

    suspend fun getLiveFeed(): Resource<List<com.kavach.app.data.remote.dto.LiveFeedEventDto>> = safeCall {
        val resp = api.getLiveFeed()
        if (resp.isSuccessful) {
            Resource.Success(resp.body()?.data ?: emptyList())
        } else {
            Resource.Error("Error fetching live feed: ${resp.message()}")
        }
    }

    suspend fun getAnalytics(): Resource<com.kavach.app.data.remote.dto.SystemAnalyticsDto> = safeCall {
        val resp = api.getAnalytics()
        if (resp.isSuccessful && resp.body()?.data != null) {
            Resource.Success(resp.body()!!.data!!)
        } else {
            Resource.Error("Error fetching analytics: ${resp.message()}")
        }
    }

    suspend fun getRemoteConfig(): Resource<Map<String, Any>> = safeCall {
        val resp = api.getRemoteConfig()
        if (resp.isSuccessful) {
            Resource.Success(resp.body()?.data ?: emptyMap())
        } else {
            Resource.Error("Error fetching config: ${resp.message()}")
        }
    }

    suspend fun updateConfig(key: String, value: Any): Resource<Unit> = safeCall {
        val resp = api.updateRemoteConfig(mapOf("key" to key, "value" to value))
        if (resp.isSuccessful) {
            Resource.Success(Unit)
        } else {
            Resource.Error("Update failed: ${resp.message()}")
        }
    }
}

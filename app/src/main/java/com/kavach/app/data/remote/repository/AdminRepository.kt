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
}

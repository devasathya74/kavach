package com.kavach.app.data.repository

import android.util.Log
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.broadcast.BroadcastDto
import com.kavach.app.data.remote.dto.incident.IncidentDto
import com.kavach.app.data.remote.dto.system.OtaUpdateDto
import com.kavach.app.data.remote.dto.v2.CreateBroadcastRequest
import com.kavach.app.data.remote.dto.v2.CreateIncidentRequest
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MissionRepository @Inject constructor(
    private val api: KavachApiV2
) {
    suspend fun getIncidents(
        page: Int = 1,
        status: String? = null,
        severity: String? = null
    ): ApiResult<List<IncidentDto>> = safeApiCall {
        val response = api.getIncidents(page, status, severity)
        ApiResult.Success(response.results.map { dto ->
            IncidentDto(
                id          = dto.id,
                title       = dto.title,
                description = dto.description,
                status      = dto.status,
                severity    = dto.severity,
                reporterPno = dto.reporterPno,
                createdAt   = dto.createdAt,
                updatedAt   = dto.updatedAt,
                mediaUrl    = dto.mediaUrl
            )
        })
    }

    suspend fun createIncident(title: String, description: String, severity: String): ApiResult<IncidentDto> = safeApiCall {
        val request = CreateIncidentRequest(
            title = title,
            description = description,
            severity = severity
        )
        val response = api.createIncident(request)
        if (response.isSuccessful && response.body()?.status == "success") {
            val dto = response.body()?.data
            if (dto != null) {
                ApiResult.Success(IncidentDto(
                    id          = dto.id,
                    title       = dto.title,
                    description = dto.description,
                    status      = dto.status,
                    severity    = dto.severity,
                    reporterPno = dto.reporterPno,
                    createdAt   = dto.createdAt,
                    updatedAt   = dto.updatedAt,
                    mediaUrl    = dto.mediaUrl
                ))
            } else {
                ApiResult.Error("Incident created but no data returned")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("CREATE_INCIDENT_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Incident creation failed")
        }
    }

    suspend fun getBroadcasts(): ApiResult<List<BroadcastDto>> = safeApiCall {
        ApiResult.Success(api.getBroadcasts().map { dto ->
            BroadcastDto(
                id           = dto.id,
                title        = dto.title,
                content      = dto.message,
                senderPno    = dto.senderPno,
                senderName   = dto.senderName,
                priority     = dto.priority,
                createdAt    = dto.createdAt,
                acknowledged = dto.acknowledged
            )
        })
    }

    suspend fun createBroadcast(
        title: String,
        content: String,
        priority: String,
        imageUrl: String? = null,
        targetedOfficerIds: List<String>? = null
    ): ApiResult<BroadcastDto> = safeApiCall {
        val request = CreateBroadcastRequest(
            title    = title,
            message  = content,
            priority = priority
        )
        
        val response = api.createBroadcast(request)
        if (response.isSuccessful && response.body()?.status == "success") {
            val dto = response.body()?.data
            if (dto != null) {
                ApiResult.Success(BroadcastDto(
                    id           = dto.id,
                    title        = dto.title,
                    content      = dto.message,
                    senderPno    = dto.senderPno,
                    senderName   = dto.senderName,
                    priority     = dto.priority,
                    createdAt    = dto.createdAt,
                    acknowledged = dto.acknowledged
                ))
            } else {
                ApiResult.Error("Broadcast created but no data returned")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("CREATE_BROADCAST_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Broadcast creation failed")
        }
    }

    suspend fun acknowledgeBroadcast(id: String): ApiResult<Unit> = safeApiCall {
        val response = api.acknowledgeBroadcast(id)
        if (response.isSuccessful && response.body()?.status == "success") {
            ApiResult.Success(Unit)
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("ACK_BROADCAST_FAIL", "Error: $errorBody")
            ApiResult.Error(response.body()?.message ?: errorBody ?: "Acknowledgment failed")
        }
    }

    suspend fun getLatestOtaUpdate(): ApiResult<OtaUpdateDto> = safeApiCall {
        val dto = api.getLatestUpdate()
        ApiResult.Success(OtaUpdateDto(
            versionCode   = dto.versionCode,
            versionName   = dto.versionName,
            isForceUpdate = dto.isForceUpdate,
            downloadUrl   = dto.downloadUrl,
            releaseNotes  = dto.releaseNotes,
            publishedAt   = dto.publishedAt
        ))
    }
}

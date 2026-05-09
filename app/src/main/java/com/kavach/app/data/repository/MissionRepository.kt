package com.kavach.app.data.repository

import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.broadcast.BroadcastDto
import com.kavach.app.data.remote.dto.incident.IncidentDto
import com.kavach.app.data.remote.dto.system.OtaUpdateDto
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
        val data = mapOf(
            "title" to title,
            "description" to description,
            "severity" to severity,
            "status" to "OPEN"
        )
        val response = api.createIncident(data)
        if (response.status == "success" && response.data != null) {
            val dto = response.data
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
            ApiResult.Error(response.message ?: "Incident creation failed")
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
        val data = mutableMapOf<String, Any>(
            "title" to title,
            "content" to content,
            "priority" to priority
        )
        imageUrl?.let { data["image_url"] = it }
        targetedOfficerIds?.let { data["targeted_officers"] = it }
        
        val response = api.createBroadcast(data)
        if (response.status == "success" && response.data != null) {
            val dto = response.data
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
            ApiResult.Error(response.message ?: "Broadcast creation failed")
        }
    }

    suspend fun acknowledgeBroadcast(id: String): ApiResult<Unit> = safeApiCall {
        val response = api.acknowledgeBroadcast(id)
        if (response.status == "success") {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error(response.message ?: "Acknowledgment failed")
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

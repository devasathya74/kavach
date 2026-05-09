package com.kavach.app.data.remote.dto.personnel

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * AdminOfficerDto — alias/superset of OfficerDto for admin API responses.
 * Used by AdminRepository and the legacy admin dashboard.
 * Maps to /api/v1/admin/users/ response structure.
 */
@JsonClass(generateAdapter = true)
data class AdminOfficerDto(
    @Json(name = "id")               val id: String,
    @Json(name = "pno")              val pno: String,
    @Json(name = "role")             val role: String,
    @Json(name = "is_active")        val isActive: Boolean = true,
    @Json(name = "is_blocked")       val isBlocked: Boolean = false,
    @Json(name = "name")             val name: String? = null,
    @Json(name = "rank")             val rank: String? = null,
    @Json(name = "unit_code")        val unitCode: String? = null,
    @Json(name = "unit_name")        val unitName: String? = null,
    @Json(name = "last_login")       val lastLogin: String? = null,
    @Json(name = "discipline_score") val disciplineScore: Double? = null,
    @Json(name = "service_status")   val serviceStatus: String? = null
)

package com.kavach.app.data.remote.dto.personnel

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OfficerDto(
    @Json(name = "id")                  val id: String,   // UUID string from v2 API
    @Json(name = "pno")                 val pno: String,
    @Json(name = "role")                val role: String,
    @Json(name = "unit")                val unit: UnitDto?,
    @Json(name = "is_active")           val isActive: Boolean,
    @Json(name = "profile")             val profile: OfficerProfileDto?,
    @Json(name = "devices")             val devices: List<DeviceDto> = emptyList(),
    @Json(name = "discipline_score")    val disciplineScore: Double? = null,
    @Json(name = "operational_level")   val operationalLevel: String? = null,
    @Json(name = "must_change_password") val mustChangePassword: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OfficerProfileDto(
    @Json(name = "name")            val name: String,
    @Json(name = "rank")            val rank: RankDto?,
    @Json(name = "unit")            val unit: UnitDto?,
    @Json(name = "company")        val company: CompanyDto? = null,
    @Json(name = "platoon")        val platoon: PlatoonDto? = null,
    @Json(name = "phone")          val phone: String? = null,
    @Json(name = "email")          val email: String? = null,
    @Json(name = "service_status") val serviceStatus: String? = null,
    @Json(name = "image")          val image: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceDto(
    @Json(name = "device_id")   val deviceId: String,
    @Json(name = "device_model") val deviceModel: String? = null,
    @Json(name = "last_active") val lastActive: String? = null,
    @Json(name = "status")      val status: String = "active"
)

@JsonClass(generateAdapter = true)
data class RankDto(
    @Json(name = "id")    val id: Int,
    @Json(name = "code")  val code: String,
    @Json(name = "name")  val name: String,
    @Json(name = "level") val level: Int
)

@JsonClass(generateAdapter = true)
data class UnitDto(
    @Json(name = "id")   val id: Int,
    @Json(name = "code") val code: String,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class CompanyDto(
    @Json(name = "id")   val id: Int,
    @Json(name = "code") val code: String,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class PlatoonDto(
    @Json(name = "id")     val id: Int,
    @Json(name = "number") val number: Int,
    @Json(name = "name")   val name: String
)

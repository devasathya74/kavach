package com.kavach.app.data.remote.dto.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.kavach.app.data.remote.dto.personnel.OfficerProfileDto
import com.kavach.app.data.remote.dto.personnel.UnitDto

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id")                    val id: String = "",
    @Json(name = "pno")                   val pno: String = "",
    @Json(name = "role")                  val role: String = "USER",
    @Json(name = "unit")                  val unit: UnitDto?,
    @Json(name = "is_active")             val isActive: Boolean = true,
    @Json(name = "profile")               val profile: OfficerProfileDto?,
    @Json(name = "must_change_password")  val mustChangePassword: Boolean = false,
    @Json(name = "discipline_score")      val disciplineScore: Int = 100,
    @Json(name = "level")                 val level: String = "L4"
)

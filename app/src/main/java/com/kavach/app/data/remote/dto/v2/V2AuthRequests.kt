package com.kavach.app.data.remote.dto.v2

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateUserRequest(
    @Json(name = "name") val name: String,
    @Json(name = "pno") val pno: String,
    @Json(name = "password") val password: String,
    @Json(name = "role") val role: String,
    @Json(name = "rank_id") val rankId: String,
    @Json(name = "unit_id") val unitId: String,
    @Json(name = "company_id") val companyId: String? = null,
    @Json(name = "platoon_id") val platoonId: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "email") val email: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "rank_id") val rankId: String? = null,
    @Json(name = "unit_id") val unitId: String? = null,
    @Json(name = "company_id") val companyId: String? = null,
    @Json(name = "platoon_id") val platoonId: String? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "email") val email: String? = null
)

@JsonClass(generateAdapter = true)
data class GenericIdRequest(
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    @Json(name = "new_password") val newPassword: String
)

@JsonClass(generateAdapter = true)
data class CreateIncidentRequest(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "severity") val severity: String,
    @Json(name = "type") val type: String? = null,
    @Json(name = "occurred_at") val occurredAt: String? = null,
    @Json(name = "status") val status: String = "OPEN"
)

@JsonClass(generateAdapter = true)
data class CreateBroadcastRequest(
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "priority") val priority: String,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "targeted_officers") val targetedOfficers: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class CreateDraftChangeRequest(
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String? = null,
    @Json(name = "action_type") val actionType: String,
    @Json(name = "changes") val changes: Map<String, String>
)

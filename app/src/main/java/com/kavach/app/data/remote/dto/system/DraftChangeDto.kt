package com.kavach.app.data.remote.dto.system

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DraftChangeDto(
    @Json(name = "id") val id: Int,
    @Json(name = "model") val model: String,
    @Json(name = "field") val field: String,
    @Json(name = "old_value") val oldValue: Map<String, Any>?,
    @Json(name = "new_value") val newValue: Map<String, Any>?,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "applied_at") val appliedAt: String? = null,
    @Json(name = "actor_name") val actorName: String? = null,
    @Json(name = "target_name") val targetName: String? = null,
    @Json(name = "target_pno") val targetPno: String? = null
)

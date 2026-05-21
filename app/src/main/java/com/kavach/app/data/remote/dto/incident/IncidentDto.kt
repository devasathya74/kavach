package com.kavach.app.data.remote.dto.incident

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IncidentDto(
    @Json(name = "id")           val id: String,
    @Json(name = "title")        val title: String,
    @Json(name = "description")  val description: String? = null,
    @Json(name = "status")       val status: String = "OPEN",
    @Json(name = "severity")     val severity: String = "MEDIUM",
    @Json(name = "reporter_pno") val reporterPno: String? = null,
    @Json(name = "created_at")   val createdAt: String? = null,
    @Json(name = "updated_at")   val updatedAt: String? = null,
    @Json(name = "media_url")    val mediaUrl: String? = null
)

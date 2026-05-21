package com.kavach.app.data.remote.dto.v2

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Device DTO ────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OfficerDeviceDto(
    @Json(name = "id")           val id: String = "",
    @Json(name = "officer_id")   val officerId: String = "",
    @Json(name = "officer_pno")  val officerPno: String? = null,
    @Json(name = "officer_name") val officerName: String? = null,
    @Json(name = "device_id")    val deviceId: String = "",
    @Json(name = "device_model") val deviceModel: String? = null,
    @Json(name = "status")       val status: String = "active",
    @Json(name = "last_active")  val lastActive: String? = null,
    @Json(name = "registered_at") val registeredAt: String? = null
)

// ── Activity / Audit DTO ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OfficerActivityDto(
    @Json(name = "id")          val id: String = "",
    @Json(name = "action")      val action: String = "",
    @Json(name = "actor_pno")   val actorPno: String? = null,
    @Json(name = "actor_name")  val actorName: String? = null,
    @Json(name = "target_pno")  val targetPno: String? = null,
    @Json(name = "target_name") val targetName: String? = null,
    @Json(name = "severity")    val severity: String = "INFO",
    @Json(name = "detail")      val detail: String? = null,
    @Json(name = "created_at")  val createdAt: String
)

// ── Incident DTO ──────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class IncidentDto(
    @Json(name = "id")          val id: String = "",
    @Json(name = "title")       val title: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "status")      val status: String = "open",
    @Json(name = "severity")    val severity: String = "medium",
    @Json(name = "reporter_pno") val reporterPno: String? = null,
    @Json(name = "created_at")  val createdAt: String = "",
    @Json(name = "updated_at")  val updatedAt: String? = null,
    @Json(name = "media_url")   val mediaUrl: String? = null
)

// ── Broadcast DTO ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class BroadcastDto(
    @Json(name = "id")             val id: String = "",
    @Json(name = "title")          val title: String = "",
    @Json(name = "message")        val message: String = "",
    @Json(name = "sender_pno")     val senderPno: String? = null,
    @Json(name = "sender_name")    val senderName: String? = null,
    @Json(name = "priority")       val priority: String = "normal",
    @Json(name = "created_at")     val createdAt: String = "",
    @Json(name = "acknowledged")   val acknowledged: Boolean = false,
    @Json(name = "recipient_unit") val recipientUnit: String? = null
)

// ── Field Data DTO ────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FieldDataDto(
    @Json(name = "id")          val id: String = "",
    @Json(name = "title")       val title: String = "",
    @Json(name = "category")    val category: String = "general",
    @Json(name = "file_url")    val fileUrl: String? = null,
    @Json(name = "uploader_pno") val uploaderPno: String? = null,
    @Json(name = "created_at")  val createdAt: String = ""
)

// ── OTA Update DTO ────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OtaUpdateDto(
    @Json(name = "version_code")      val versionCode: Int = 0,
    @Json(name = "version_name")      val versionName: String = "",
    @Json(name = "is_force_update")   val isForceUpdate: Boolean = false,
    @Json(name = "download_url")      val downloadUrl: String? = null,
    @Json(name = "release_notes")     val releaseNotes: String? = null,
    @Json(name = "published_at")      val publishedAt: String? = null
)
// ── Order DTO ─────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "id")          val id: String = "",
    @Json(name = "title")       val title: String = "",
    @Json(name = "content")     val content: String = "",
    @Json(name = "type")        val type: String = "GENERAL",
    @Json(name = "status")      val status: String = "PENDING",
    @Json(name = "issued_by")   val issuedBy: String = "",
    @Json(name = "issued_at")   val issuedAt: Long = 0L,
    @Json(name = "expires_at")  val expiresAt: Long? = null
)

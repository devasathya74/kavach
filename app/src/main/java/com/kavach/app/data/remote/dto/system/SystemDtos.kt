package com.kavach.app.data.remote.dto.system

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── App Update ────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class UpdateInfoDto(
    @Json(name = "version_code")      val versionCode: Int,
    @Json(name = "version_name")      val versionName: String? = null,
    @Json(name = "min_supported_version") val minSupportedVersion: Int = 1,
    @Json(name = "force_update")      val forceUpdate: Boolean = false,
    @Json(name = "is_rollback")       val isRollback: Boolean = false,
    @Json(name = "is_critical")       val isCritical: Boolean = false,
    @Json(name = "channel")           val channel: String = "stable",
    @Json(name = "release_notes")     val releaseNotes: String? = null,
    @Json(name = "download_url")      val downloadUrl: String? = null,
    @Json(name = "schema_version")    val schemaVersion: Int = 1,
    @Json(name = "apk_size")         val apkSize: Long? = null,
    @Json(name = "apk_hash")         val apkHash: String? = null
)

// ── Admin / System ────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AdminUserActionDto(
    @Json(name = "pno")    val pno: String,
    @Json(name = "action") val action: String,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceChangeRequest(
    @Json(name = "reason")     val reason: String,
    @Json(name = "new_device_id") val newDeviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceChangeResponse(
    @Json(name = "request_id") val requestId: String,
    @Json(name = "status")     val status: String,
    @Json(name = "message")    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveFeedEventDto(
    @Json(name = "id")          val id: String,
    @Json(name = "event_type")  val eventType: String,
    @Json(name = "actor_pno")   val actorPno: String? = null,
    @Json(name = "actor_name")  val actorName: String? = null,
    @Json(name = "detail")      val detail: String? = null,
    @Json(name = "severity")    val severity: String = "INFO",
    @Json(name = "created_at")  val createdAt: String
)

@JsonClass(generateAdapter = true)
data class SystemAnalyticsDto(
    @Json(name = "total_officers")    val totalOfficers: Int = 0,
    @Json(name = "active_sessions")   val activeSessions: Int = 0,
    @Json(name = "incidents_today")   val incidentsToday: Int = 0,
    @Json(name = "compliance_rate")   val complianceRate: Double = 0.0,
    @Json(name = "avg_behavior_score") val avgBehaviorScore: Double = 0.0,
    @Json(name = "pending_approvals") val pendingApprovals: Int = 0
)

// ── Behavior ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class BehaviorEventDto(
    @Json(name = "event_type")   val eventType: String,
    @Json(name = "training_id")  val trainingId: Int? = null,
    @Json(name = "timestamp_ms") val timestampMs: Long = System.currentTimeMillis(),
    @Json(name = "metadata")     val metadata: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class BehaviorBatchRequest(
    @Json(name = "events")     val events: List<BehaviorEventDto>,
    @Json(name = "device_id")  val deviceId: String? = null,
    @Json(name = "session_id") val sessionId: String? = null
)

@JsonClass(generateAdapter = true)
data class UserScoreDto(
    @Json(name = "score")         val score: Double,
    @Json(name = "grade")         val grade: String = "B",
    @Json(name = "percentile")    val percentile: Double? = null,
    @Json(name = "last_updated")  val lastUpdated: String? = null
)

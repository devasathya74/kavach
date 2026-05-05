package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Auth ──────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "pno")       val pno      : String,
    @Json(name = "device_id") val deviceId : String
)

@JsonClass(generateAdapter = true)
data class AdminLoginRequest(
    @Json(name = "pno")       val pno      : String,
    @Json(name = "password")  val password : String,
    @Json(name = "device_id") val deviceId : String
)

@JsonClass(generateAdapter = true)
data class OtpRequest(
    @Json(name = "pno")       val pno      : String,
    @Json(name = "otp")       val otp      : String,
    @Json(name = "device_id") val deviceId : String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "status")        val status       : String,
    @Json(name = "token")         val token        : String?,
    @Json(name = "refresh_token") val refreshToken : String?,
    @Json(name = "expires_in")    val expiresIn    : Long?,
    @Json(name = "user")          val user         : UserDto?
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id")        val id: String,
    @Json(name = "pno")       val pno: String,
    @Json(name = "name")      val name: String,
    @Json(name = "rank")      val rank: String,
    @Json(name = "unit")      val unit: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "is_staff")  val isStaff: Boolean
)

@JsonClass(generateAdapter = true)
data class TrainingDto(
    @Json(name = "id")           val id: Int,
    @Json(name = "title")        val title: String,
    @Json(name = "description")  val description: String,
    @Json(name = "video_url")    val videoUrl: String,
    @Json(name = "duration")     val duration: Int,
    @Json(name = "is_mandatory") val isMandatory: Boolean,
    @Json(name = "status")       val status: String
)

@JsonClass(generateAdapter = true)
data class QuizQuestionDto(
    @Json(name = "id")             val id: Int,
    @Json(name = "training_id")    val trainingId: Int,
    @Json(name = "question")       val question: String,
    @Json(name = "option_a")       val optionA: String,
    @Json(name = "option_b")       val optionB: String,
    @Json(name = "option_c")       val optionC: String,
    @Json(name = "option_d")       val optionD: String,
    @Json(name = "correct_option") val correctOption: String
)

@JsonClass(generateAdapter = true)
data class QuizSubmitRequest(
    @Json(name = "training_id") val trainingId: Int,
    @Json(name = "answers")     val answers: Map<Int, String>
)

@JsonClass(generateAdapter = true)
data class QuizResultDto(
    @Json(name = "score")  val score: Int,
    @Json(name = "passed") val passed: Boolean,
    @Json(name = "total")  val total: Int
)

@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "id")             val id: String,
    @Json(name = "title")          val title: String,
    @Json(name = "content_text")   val contentText: String?,
    @Json(name = "image_url")      val imageUrl: String?,
    @Json(name = "issued_by")      val issuedBy: String,
    @Json(name = "created_at")     val createdAt: Long,
    @Json(name = "is_mandatory")   val isMandatory: Boolean,
    @Json(name = "is_acknowledged")val isAcknowledged: Boolean
)

@JsonClass(generateAdapter = true)
data class AcknowledgeRequest(
    @Json(name = "order_id")        val orderId       : String,
    @Json(name = "device_id")       val deviceId      : String,
    @Json(name = "timestamp")       val timestamp     : Long,
    @Json(name = "read_duration")   val readDuration  : Long,
    @Json(name = "idempotency_key") val idempotencyKey: String
)

@JsonClass(generateAdapter = true)
data class BehaviorEventDto(
    @Json(name = "event_type")   val eventType  : String,
    @Json(name = "training_id")  val trainingId : Int?,
    @Json(name = "timestamp_ms") val timestampMs: Long,
    @Json(name = "metadata")     val metadata   : Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class BehaviorBatchRequest(
    @Json(name = "events") val events: List<BehaviorEventDto>
)

data class ApiResponse<T>(
    @Json(name = "status")  val status: String,
    @Json(name = "message") val message: String?,
    @Json(name = "data")    val data: T?
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "training_id") val trainingId : Int,
    @Json(name = "position_ms") val positionMs : Long,
    @Json(name = "session_id")  val sessionId  : String
)

@JsonClass(generateAdapter = true)
data class UpdateInfoDto(
    @Json(name = "version_code")  val versionCode: Int,
    @Json(name = "apk_url")       val downloadUrl: String,
    @Json(name = "apk_hash")      val apkHash: String,
    @Json(name = "apk_size")      val apkSize: Long,
    @Json(name = "force_update")  val forceUpdate: Boolean,
    @Json(name = "release_notes") val releaseNotes: String
)

@JsonClass(generateAdapter = true)
data class DeviceChangeRequest(
    @Json(name = "pno")           val pno          : String,
    @Json(name = "new_device_id") val newDeviceId  : String,
    @Json(name = "reason")        val reason       : String,
    @Json(name = "contact_info")  val contactInfo  : String
)

@JsonClass(generateAdapter = true)
data class DeviceChangeResponse(
    @Json(name = "request_id") val requestId : String,
    @Json(name = "status")     val status    : String,
    @Json(name = "message")    val message   : String?
)

@JsonClass(generateAdapter = true)
data class AdminUserActionDto(
    @Json(name = "pno")         val pno         : String,
    @Json(name = "action")      val action      : String,
    @Json(name = "training_id") val trainingId  : Int? = null,
    @Json(name = "reason")      val reason      : String = ""
)

@JsonClass(generateAdapter = true)
data class AdminOfficerDto(
    @Json(name = "pno")                 val pno: String,
    @Json(name = "name")                val name: String,
    @Json(name = "rank")                val rank: String,
    @Json(name = "unit")                val unit: String,
    @Json(name = "is_blocked")          val isBlocked: Boolean,
    @Json(name = "discipline_score")    val disciplineScore: Int,
    @Json(name = "grade")               val grade: String,
    @Json(name = "requires_review")     val requiresReview: Boolean,
    @Json(name = "trainings_done")      val trainingsDone: Int,
    @Json(name = "suspicious_sessions") val suspiciousSessions: Int
)

@JsonClass(generateAdapter = true)
data class UserScoreDto(
    @Json(name = "pno")             val pno            : String,
    @Json(name = "discipline_score")val disciplineScore: Int,
    @Json(name = "grade")           val grade          : String,
    @Json(name = "requires_review") val requiresReview : Boolean,
    @Json(name = "event_summary")   val eventSummary   : Map<String, Int>
)

@JsonClass(generateAdapter = true)
data class ConsentRequest(
    @Json(name = "pno")          val pno          : String,
    @Json(name = "device_id")    val deviceId     : String,
    @Json(name = "accepted_at")  val acceptedAt   : String,
    @Json(name = "app_version")  val appVersion   : String
)

@JsonClass(generateAdapter = true)
data class QuizSubmitRequestV2(
    @Json(name = "training_id")    val trainingId    : Int,
    @Json(name = "answers")        val answers       : Map<Int, String>,
    @Json(name = "session_id")     val sessionId     : String,
    @Json(name = "time_taken_ms")  val timeTakenMs   : Long,
    @Json(name = "answer_timings") val answerTimings : Map<Int, Long>
)

@JsonClass(generateAdapter = true)
data class SuspiciousSessionDto(
    @Json(name = "pno")               val pno              : String,
    @Json(name = "name")              val name             : String,
    @Json(name = "training")          val training         : String,
    @Json(name = "suspicious_reason") val suspiciousReason : String,
    @Json(name = "quiz_score")        val quizScore        : Int?,
    @Json(name = "heartbeat_count")   val heartbeatCount   : Int,
    @Json(name = "completed_at")      val completedAt      : String?
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "device_id")     val deviceId    : String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenResponse(
    @Json(name = "status")        val status       : String,
    @Json(name = "access_token")  val accessToken  : String?,
    @Json(name = "refresh_token") val refreshToken : String?,
    @Json(name = "expires_in")    val expiresIn    : Long?
)

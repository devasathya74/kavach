package com.kavach.app.data.remote.dto.training

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrainingDto(
    @Json(name = "id")           val id: String = "",
    @Json(name = "title")        val title: String = "",
    @Json(name = "description")  val description: String? = null,
    @Json(name = "video_path")   val videoPath: String? = null,
    @Json(name = "duration_sec") val durationSec: Int = 0,
    @Json(name = "is_mandatory") val isMandatory: Boolean = false,
    @Json(name = "created_at")   val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "training_id")  val trainingId: String,
    @Json(name = "position_ms")  val positionMs: Long = 0,
    @Json(name = "elapsed_sec")  val elapsedSec: Int = 0,
    @Json(name = "session_id")   val sessionId: String? = null,
    @Json(name = "device_id")    val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class QuizQuestionDto(
    @Json(name = "id")           val id: String,
    @Json(name = "question")     val question: String,
    @Json(name = "options")      val options: List<String>,
    @Json(name = "training_id")  val trainingId: String
)

@JsonClass(generateAdapter = true)
data class QuizSubmitRequest(
    @Json(name = "training_id") val trainingId: String,
    @Json(name = "answers")     val answers: Map<String, String>
)

@JsonClass(generateAdapter = true)
data class QuizSubmitRequestV2(
    @Json(name = "training_id") val trainingId: String,
    @Json(name = "answers")     val answers: Map<String, String>,
    @Json(name = "time_taken")  val timeTaken: Int = 0
)

@JsonClass(generateAdapter = true)
data class QuizResultDto(
    @Json(name = "score")         val score: Int,
    @Json(name = "total")         val total: Int,
    @Json(name = "passed")        val passed: Boolean,
    @Json(name = "feedback")      val feedback: String? = null
)

@JsonClass(generateAdapter = true)
data class SuspiciousSessionDto(
    @Json(name = "id")          val id: String,
    @Json(name = "officer_pno") val officerPno: String,
    @Json(name = "device_id")   val deviceId: String? = null,
    @Json(name = "reason")      val reason: String? = null,
    @Json(name = "created_at")  val createdAt: String? = null,
    @Json(name = "severity")    val severity: String = "medium"
)

/**
 * TrainingModuleDto — richer training module used by the v2 Training screens.
 * Includes completion tracking (isCompleted) and mandatory flag.
 */
@JsonClass(generateAdapter = true)
data class TrainingModuleDto(
    @Json(name = "id")           val id: String = "",
    @Json(name = "title")        val title: String = "",
    @Json(name = "description")  val description: String = "",
    @Json(name = "video_url")    val videoUrl: String? = null,
    @Json(name = "duration_sec") val durationSec: Int = 0,
    @Json(name = "is_mandatory") val isMandatory: Boolean = false,
    @Json(name = "is_completed") val isCompleted: Boolean = false,
    @Json(name = "acknowledged") val acknowledged: Boolean = false,
    @Json(name = "created_at")   val createdAt: String? = null
)

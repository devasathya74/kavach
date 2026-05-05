package com.kavach.app.utils

import com.kavach.app.data.local.dao.BehaviorEventDao
import com.kavach.app.data.local.entity.BehaviorEventEntity
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.BehaviorBatchRequest
import com.kavach.app.data.remote.dto.BehaviorEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BehaviorTracker — passive misuse detection system.
 *
 * What it tracks:
 *  • SEEK_ATTEMPT       — user tried to skip video forward
 *  • APP_BACKGROUND     — app sent to background during video playback
 *  • APP_FOREGROUND     — app returned from background
 *  • SCREENSHOT_ATTEMPT — detected via WindowManager flag (when implemented)
 *  • QUIZ_FAST_ANSWER   — answered a question below MIN_ANSWER_TIME
 *  • QUIZ_ATTEMPT       — every quiz submission (pass or fail)
 *  • TRAINING_SKIP      — video marked complete before actual duration
 *
 * Architecture:
 *  1. Events logged locally to Room DB (fire-and-forget, never blocks UI).
 *  2. BehaviorSyncWorker batches and uploads them to server periodically.
 *  3. Server-side analytics flag suspicious patterns (fast quizzes, repeated seeks).
 *
 * Admin dashboard shows:
 *  • Users with SEEK_ATTEMPT count > threshold → flagged for review
 *  • Users who completed training in < 50% of video duration → suspicious
 */
@Singleton
class BehaviorTracker @Inject constructor(
    private val behaviorEventDao : BehaviorEventDao,
    private val api              : KavachApiService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Event Type Constants ───────────────────────────────

    object Events {
        const val SEEK_ATTEMPT       = "SEEK_ATTEMPT"
        const val APP_BACKGROUND     = "APP_BACKGROUND"
        const val APP_FOREGROUND     = "APP_FOREGROUND"
        const val QUIZ_FAST_ANSWER   = "QUIZ_FAST_ANSWER"
        const val QUIZ_ATTEMPT       = "QUIZ_ATTEMPT"
        const val TRAINING_COMPLETE  = "TRAINING_COMPLETE"
        const val TRAINING_SKIP      = "TRAINING_SKIP"
        const val LOGIN_NEW_DEVICE   = "LOGIN_NEW_DEVICE"
        const val DEVICE_MISMATCH    = "DEVICE_MISMATCH"
    }

    // ── Public API ─────────────────────────────────────────

    /**
     * Log an event. Fire-and-forget — never throws, never blocks.
     */
    fun log(
        eventType  : String,
        trainingId : Int?                = null,
        metadata   : Map<String, String> = emptyMap()
    ) {
        scope.launch {
            try {
                val entity = BehaviorEventEntity(
                    eventType   = eventType,
                    trainingId  = trainingId,
                    timestampMs = System.currentTimeMillis(),
                    metadata    = metadata.entries.joinToString(",") { "${it.key}=${it.value}" }
                )
                behaviorEventDao.insert(entity)
                behaviorEventDao.trimOldEvents()   // enforce 500-event cap
            } catch (_: Exception) {
                // Never crash the app for tracking
            }
        }
    }

    // ── Convenience helpers ────────────────────────────────

    fun logSeekAttempt(trainingId: Int, positionMs: Long, maxAllowedMs: Long) = log(
        eventType  = Events.SEEK_ATTEMPT,
        trainingId = trainingId,
        metadata   = mapOf(
            "attempted_pos_ms" to positionMs.toString(),
            "max_allowed_ms"   to maxAllowedMs.toString()
        )
    )

    fun logAppBackground(trainingId: Int, elapsedSeconds: Long) = log(
        eventType  = Events.APP_BACKGROUND,
        trainingId = trainingId,
        metadata   = mapOf("elapsed_s" to elapsedSeconds.toString())
    )

    fun logQuizFastAnswer(trainingId: Int, questionId: Int, elapsedMs: Long) = log(
        eventType  = Events.QUIZ_FAST_ANSWER,
        trainingId = trainingId,
        metadata   = mapOf(
            "question_id" to questionId.toString(),
            "elapsed_ms"  to elapsedMs.toString()
        )
    )

    fun logQuizAttempt(trainingId: Int, score: Int, passed: Boolean) = log(
        eventType  = Events.QUIZ_ATTEMPT,
        trainingId = trainingId,
        metadata   = mapOf(
            "score"  to score.toString(),
            "passed" to passed.toString()
        )
    )

    fun logDeviceMismatch(pno: String) = log(
        eventType  = Events.DEVICE_MISMATCH,
        trainingId = null,
        metadata   = mapOf("pno" to pno)
    )

    // ── Batch Sync (called by BehaviorSyncWorker) ──────────

    /**
     * Upload buffered events to server in one batch.
     * Called by WorkManager — safe to call frequently, server deduplicates.
     */
    suspend fun syncToServer(): Resource<Unit> = safeCall {
        val events = behaviorEventDao.getAll()
        if (events.isEmpty()) return@safeCall Resource.Success(Unit)

        val dtos = events.map { entity ->
            BehaviorEventDto(
                eventType   = entity.eventType,
                trainingId  = entity.trainingId,
                timestampMs = entity.timestampMs,
                metadata    = entity.metadata
                    .split(",")
                    .mapNotNull { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }
                    .toMap()
            )
        }

        val resp = api.sendBehaviorEvents(BehaviorBatchRequest(events = dtos))
        if (resp.isSuccessful) {
            behaviorEventDao.deleteByIds(events.map { it.id })
            Resource.Success(Unit)
        } else {
            Resource.Error("Behavior sync failed: ${resp.code()}")
        }
    }
}

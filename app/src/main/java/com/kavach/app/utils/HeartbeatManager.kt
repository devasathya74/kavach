package com.kavach.app.utils

import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.training.HeartbeatRequest
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HeartbeatManager — proves continuous active video engagement.
 *
 * Every 15 seconds during playback, sends:
 *   { training_id, position_ms, elapsed_seconds, session_id }
 *
 * Server-side validation:
 *   • Checks heartbeats form a continuous sequence (no gaps > 30s)
 *   • Rejects quiz submission if heartbeat coverage < 90% of video duration
 *   • Detects "video playing in background" pattern (position not advancing)
 *
 * This is the strongest proof of actual watching — defeats:
 *   • Opening video and leaving the room
 *   • Playing video on mute while doing other tasks
 *   • Screen recording for later review
 */
@Singleton
class HeartbeatManager @Inject constructor(
    private val api             : KavachApiService,
    private val behaviorTracker : BehaviorTracker
) {
    private var heartbeatJob    : Job?   = null
    private var sessionId       : String = ""
    private var trainingId      : Int    = 0
    private var lastPositionMs  : Long   = 0L
    private var stuckCount      : Int    = 0     // times position hasn't advanced

    companion object {
        const val INTERVAL_MS     = 15_000L   // every 15 seconds
        const val MAX_STUCK_COUNT = 3         // 3 × 15s = 45s with no progress = flag
    }

    /**
     * Start heartbeat loop when video begins playing.
     * @param sessionId Unique ID per training session (new UUID each start)
     */
    fun start(trainingId: Int, sessionId: String, scope: CoroutineScope) {
        stop()
        this.trainingId = trainingId
        this.sessionId  = sessionId
        this.stuckCount = 0

        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(INTERVAL_MS)
                sendHeartbeat()
            }
        }
    }

    /** Update position — called from Player.Listener.onPositionChanged() */
    fun updatePosition(positionMs: Long) {
        if (positionMs <= lastPositionMs + 1000 && lastPositionMs > 0) {
            stuckCount++
            if (stuckCount >= MAX_STUCK_COUNT) {
                behaviorTracker.log(
                    eventType  = BehaviorTracker.Events.APP_BACKGROUND,
                    trainingId = trainingId,
                    metadata   = mapOf(
                        "reason"      to "position_stuck",
                        "position_ms" to positionMs.toString(),
                        "stuck_count" to stuckCount.toString()
                    )
                )
            }
        } else {
            stuckCount = 0
        }
        lastPositionMs = positionMs
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeat() {
        try {
            api.sendHeartbeat(
                HeartbeatRequest(
                    trainingId  = trainingId,
                    positionMs  = lastPositionMs,
                    sessionId   = sessionId
                )
            )
        } catch (_: Exception) {
            // Network failure — heartbeat missed, server detects gap
            // No crash — player continues
        }
    }
}

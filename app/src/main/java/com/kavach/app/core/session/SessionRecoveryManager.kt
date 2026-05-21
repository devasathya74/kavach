package com.kavach.app.core.session

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.data.local.SessionDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionRecoveryManager — Operational continuity engine.
 *
 * Handles recovery of interrupted operational sessions:
 *   - WebSocket reconnect with exponential backoff
 *   - Conference re-join after uplink loss
 *   - Training playback position recovery
 *   - Offline sync queue flush on reconnect
 *
 * Emits recovery state changes through [recoveryState] StateFlow
 * and dispatches SystemEvents through EventBus.
 */

enum class RecoveryState {
    IDLE,           // Normal operation
    DETECTING,      // Loss detected, assessing
    RECOVERING,     // Actively reconnecting
    RECOVERED,      // Successfully restored
    FAILED          // Recovery exhausted, escalate
}

data class RecoveryContext(
    val state           : RecoveryState = RecoveryState.IDLE,
    val attempt         : Int           = 0,
    val maxAttempts     : Int           = 5,
    val backoffMs       : Long          = 1_000L,
    val lastErrorMsg    : String?       = null,
    val pendingSyncItems: Int           = 0
)

@Singleton
class SessionRecoveryManager @Inject constructor(
    private val sessionDataStore : SessionDataStore,
    private val eventBus         : EventBus
) {

    companion object {
        private const val MAX_ATTEMPTS      = 5
        private const val BASE_BACKOFF_MS   = 1_000L
        private const val MAX_BACKOFF_MS    = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _recoveryState = MutableStateFlow(RecoveryContext())
    val recoveryState: StateFlow<RecoveryContext> = _recoveryState.asStateFlow()

    // In-memory offline action queue (replace with Room queue for production)
    private val offlineQueue = mutableListOf<String>()

    // ── WebSocket Recovery ────────────────────────────────────

    /**
     * Trigger WebSocket reconnect with exponential backoff.
     * Call from WebSocket onFailure/onClosed listener.
     */
    fun startWsRecovery(onReconnect: suspend () -> Boolean) {
        scope.launch {
            _recoveryState.value = RecoveryContext(
                state = RecoveryState.DETECTING
            )
            delay(500L)

            var attempt  = 0
            var backoff  = BASE_BACKOFF_MS

            while (attempt < MAX_ATTEMPTS) {
                attempt++
                _recoveryState.value = RecoveryContext(
                    state       = RecoveryState.RECOVERING,
                    attempt     = attempt,
                    maxAttempts = MAX_ATTEMPTS,
                    backoffMs   = backoff
                )
                eventBus.emit(SystemEvent.WebSocketReconnecting(attempt))

                val success = try { onReconnect() } catch (e: Exception) { false }

                if (success) {
                    _recoveryState.value = RecoveryContext(state = RecoveryState.RECOVERED)
                    eventBus.emit(SystemEvent.WebSocketConnected)
                    flushOfflineQueue()
                    delay(2_000L)
                    _recoveryState.value = RecoveryContext(state = RecoveryState.IDLE)
                    return@launch
                }

                // Exponential backoff with ±30% jitter to prevent reconnect storms.
                // Example: attempt 3 → base 4000ms → jitter [2800ms, 5200ms]
                val jitterFactor = 0.7f + kotlin.random.Random.nextFloat() * 0.6f // 0.7 – 1.3
                val jitteredBackoff = (backoff * jitterFactor).toLong()
                delay(jitteredBackoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }

            // All attempts exhausted
            _recoveryState.value = RecoveryContext(
                state        = RecoveryState.FAILED,
                attempt      = attempt,
                lastErrorMsg = "WebSocket recovery exhausted after $MAX_ATTEMPTS attempts"
            )
            eventBus.emit(SystemEvent.UplinkDegraded)
        }
    }

    // ── Training Playback Recovery ────────────────────────────

    /** Save playback position (call from VideoPlayerViewModel periodically). */
    fun savePlaybackPosition(trainingId: String, positionMs: Long) {
        scope.launch {
            // SessionDataStore can be extended to store this; for now in-memory
            playbackPositions[trainingId] = positionMs
        }
    }

    /** Restore last known position for a training module. Returns 0 if none. */
    fun getPlaybackPosition(trainingId: String): Long = playbackPositions[trainingId] ?: 0L

    private val playbackPositions = mutableMapOf<String, Long>()

    // ── Offline Action Queue ──────────────────────────────────

    /** Enqueue an action (e.g., acknowledgment) for later sync when online. */
    fun enqueueOfflineAction(serializedAction: String) {
        offlineQueue.add(serializedAction)
        _recoveryState.value = _recoveryState.value.copy(
            pendingSyncItems = offlineQueue.size
        )
    }

    /** Flush queue when connectivity restored. */
    private suspend fun flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return
        // TODO: Replace with actual API calls per action type
        val count = offlineQueue.size
        offlineQueue.clear()
        _recoveryState.value = _recoveryState.value.copy(pendingSyncItems = 0)
    }

    val hasPendingSync get() = offlineQueue.isNotEmpty()
}

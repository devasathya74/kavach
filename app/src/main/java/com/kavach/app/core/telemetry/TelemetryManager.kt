package com.kavach.app.core.telemetry

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.ThreatStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

/**
 * TelemetryManager — Persistent background telemetry engine.
 *
 * Runs continuously after [start] is called (typically from Application/ViewModel).
 * Emits [NetworkTelemetry] snapshots to [telemetry] at [intervalMs] intervals.
 *
 * Also monitors quality drift and auto-escalates ThreatLevel via ThreatStateManager
 * when connectivity degrades beyond thresholds.
 */
@Singleton
class TelemetryManager @Inject constructor(
    private val threatStateManager : ThreatStateManager,
    private val eventBus           : EventBus
) {

    companion object {
        private const val INTERVAL_MS           = 3_000L
        private const val HEARTBEAT_MS          = 5_000L
        private const val RTT_WARN_THRESHOLD    = 800L
        private const val RTT_CRIT_THRESHOLD    = 2_000L
        private const val LOSS_WARN_THRESHOLD   = 8f
        private const val LOSS_CRIT_THRESHOLD   = 25f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _telemetry = MutableStateFlow(NetworkTelemetry())
    val telemetry: StateFlow<NetworkTelemetry> = _telemetry.asStateFlow()

    // Mutable fields updated by network layer (call these from Retrofit interceptors / WS)
    @Volatile var lastApiRttMs          : Long  = 45L
    @Volatile var lastWsLatencyMs       : Long  = 22L
    @Volatile var reconnectAttempts     : Int   = 0
    @Volatile var syncQueueSize         : Int   = 0
    @Volatile var conferenceBitrateKbps : Int   = 0
    @Volatile var uplinkStatus          : UplinkStatus = UplinkStatus.CONNECTED

    private var job: Job? = null

    /** Start the telemetry polling loop. Idempotent. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                tick()
                delay(INTERVAL_MS)
            }
        }
    }

    /** Stop the telemetry engine (e.g., on logout). */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Record a real API RTT measurement (call from OkHttp interceptor). */
    fun recordApiRtt(ms: Long) { lastApiRttMs = ms }

    /** Record WS latency from ping/pong (call from WebSocket listener). */
    fun recordWsLatency(ms: Long) { lastWsLatencyMs = ms }

    /** Call when WebSocket reconnects start/succeed. */
    fun onWsReconnecting(attempt: Int) {
        reconnectAttempts = attempt
        uplinkStatus = UplinkStatus.RECONNECTING
        eventBus.emit(SystemEvent.WebSocketReconnecting(attempt))
    }

    fun onWsConnected() {
        reconnectAttempts = 0
        uplinkStatus = UplinkStatus.CONNECTED
        eventBus.emit(SystemEvent.WebSocketConnected)
    }

    fun onUplinkOffline() {
        uplinkStatus = UplinkStatus.OFFLINE
        eventBus.emit(SystemEvent.UplinkDegraded)
    }

    fun onUplinkRestored() {
        uplinkStatus = UplinkStatus.CONNECTED
        eventBus.emit(SystemEvent.UplinkRestored)
    }

    private fun tick() {
        // Simulate realistic packet loss (small jitter even on good connections)
        val simulatedLoss = when (uplinkStatus) {
            UplinkStatus.OFFLINE       -> Random.nextFloat() * 80f + 20f
            UplinkStatus.RECONNECTING  -> Random.nextFloat() * 30f + 10f
            UplinkStatus.DEGRADED      -> Random.nextFloat() * 15f + 5f
            UplinkStatus.CONNECTED     -> Random.nextFloat() * 3f         // ≤ 3% noise
        }

        val snapshot = NetworkTelemetry(
            apiRttMs              = lastApiRttMs,
            wsLatencyMs           = lastWsLatencyMs,
            packetLossPct         = simulatedLoss,
            reconnectAttempts     = reconnectAttempts,
            heartbeatIntervalMs   = HEARTBEAT_MS,
            syncQueueSize         = syncQueueSize,
            conferenceBitrateKbps = conferenceBitrateKbps,
            uplinkStatus          = uplinkStatus,
        )

        _telemetry.value = snapshot

        // ── Auto threat escalation based on telemetry ─────────────
        val newThreat = when {
            uplinkStatus == UplinkStatus.OFFLINE            -> ThreatLevel.ELEVATED
            simulatedLoss > LOSS_CRIT_THRESHOLD             -> ThreatLevel.CRITICAL
            simulatedLoss > LOSS_WARN_THRESHOLD             -> ThreatLevel.WARNING
            lastApiRttMs > RTT_CRIT_THRESHOLD               -> ThreatLevel.ELEVATED
            lastApiRttMs > RTT_WARN_THRESHOLD               -> ThreatLevel.WARNING
            uplinkStatus == UplinkStatus.CONNECTED          -> ThreatLevel.SECURE
            else                                             -> ThreatLevel.WARNING
        }

        // Only escalate — don't auto-de-escalate (requires explicit clearance)
        if (newThreat > threatStateManager.currentLevel.value) {
            threatStateManager.escalateTo(newThreat)
        } else if (newThreat == ThreatLevel.SECURE &&
                   threatStateManager.currentLevel.value == ThreatLevel.WARNING) {
            threatStateManager.setLevel(ThreatLevel.SECURE)
        }
    }
}

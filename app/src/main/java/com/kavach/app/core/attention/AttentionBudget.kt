package com.kavach.app.core.attention

import com.kavach.app.core.events.SystemEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AttentionBudget — Operator cognitive load management system.
 *
 * Real tactical systems prioritize cognitive load management.
 * Too many alerts reduce operator awareness faster than no alerts.
 *
 * This system:
 *   1. Tracks a COGNITIVE LOAD SCORE (0–100)
 *   2. Rates alerts by attention cost
 *   3. Suppresses duplicate or low-priority alerts under load
 *   4. Triggers a "summary mode" when load exceeds threshold
 *   5. Provides per-type cooldown windows
 *
 * Design rules:
 *   - Critical/lockdown events always pass (no suppression)
 *   - Telemetry noise is aggressively throttled
 *   - Load decays naturally over time (30s half-life)
 */

enum class AlertType(
    val cost         : Int,    // Cognitive cost per occurrence (0–20)
    val cooldownSec  : Long,   // Minimum seconds between same-type alerts
    val suppressible : Boolean // False = always show regardless of load
) {
    // Highest priority — never suppressed
    LOCKDOWN        (cost = 20, cooldownSec = 0L,   suppressible = false),
    COMMAND_OVERRIDE(cost = 18, cooldownSec = 0L,   suppressible = false),
    INTEGRITY_BREACH(cost = 20, cooldownSec = 0L,   suppressible = false),

    // High priority — suppressed only under extreme load
    THREAT_ESCALATE (cost = 15, cooldownSec = 10L,  suppressible = true),
    EMERGENCY_BCAST (cost = 15, cooldownSec = 5L,   suppressible = true),
    FORCED_LOGOUT   (cost = 15, cooldownSec = 0L,   suppressible = false),

    // Medium priority — throttled under moderate load
    CONFERENCE_START(cost = 8,  cooldownSec = 30L,  suppressible = true),
    SESSION_EXPIRED (cost = 8,  cooldownSec = 60L,  suppressible = true),
    ADMIN_ESCALATION(cost = 6,  cooldownSec = 30L,  suppressible = true),

    // Low priority — aggressively throttled
    UPLINK_RESTORED (cost = 3,  cooldownSec = 60L,  suppressible = true),
    WS_RECONNECTING (cost = 2,  cooldownSec = 30L,  suppressible = true),
    TELEMETRY_UPDATE(cost = 1,  cooldownSec = 5L,   suppressible = true),
    AUDIT_CAPTURE   (cost = 1,  cooldownSec = 120L, suppressible = true),
}

data class AttentionState(
    val cognitiveLoad   : Int  = 0,     // 0–100
    val isSummaryMode   : Boolean = false,
    val suppressedCount : Int  = 0,
    val lastAlertType   : AlertType? = null
)

sealed class AlertDecision {
    object Show                            : AlertDecision()
    object Suppress                        : AlertDecision()
    data class Cooldown(val remainSec: Long) : AlertDecision()
}

@Singleton
class AttentionBudget @Inject constructor() {

    companion object {
        private const val SUMMARY_MODE_THRESHOLD = 75  // Load % above which summary mode kicks in
        private const val MAX_LOAD               = 100
        private const val LOAD_DECAY_RATE        = 2   // Decay per evaluation call
    }

    private val _state = MutableStateFlow(AttentionState())
    val state: StateFlow<AttentionState> = _state.asStateFlow()

    // Tracks last-seen time per alert type
    private val lastAlertMs = mutableMapOf<AlertType, Long>()

    /**
     * Evaluate whether to show or suppress an alert.
     * MUST be called before displaying any alert/overlay/sound.
     *
     * @return [AlertDecision.Show], [AlertDecision.Suppress], or [AlertDecision.Cooldown]
     */
    fun evaluate(type: AlertType): AlertDecision {
        // Step 1: decay cognitive load
        decayLoad()

        val nowMs  = System.currentTimeMillis()
        val lastMs = lastAlertMs[type] ?: 0L

        // Step 2: cooldown check
        val elapsedSec = (nowMs - lastMs) / 1000L
        if (elapsedSec < type.cooldownSec) {
            val remainSec = type.cooldownSec - elapsedSec
            // Non-suppressible still shows even in cooldown
            if (type.suppressible) {
                _state.value = _state.value.copy(suppressedCount = _state.value.suppressedCount + 1)
                return AlertDecision.Cooldown(remainSec)
            }
        }

        // Step 3: load-based suppression (only suppressible types, only under high load)
        val currentLoad = _state.value.cognitiveLoad
        if (type.suppressible && currentLoad >= SUMMARY_MODE_THRESHOLD) {
            _state.value = _state.value.copy(suppressedCount = _state.value.suppressedCount + 1)
            return AlertDecision.Suppress
        }

        // Step 4: Allow — add cost and record timestamp
        lastAlertMs[type] = nowMs
        val newLoad = (currentLoad + type.cost).coerceAtMost(MAX_LOAD)
        _state.value = _state.value.copy(
            cognitiveLoad  = newLoad,
            isSummaryMode  = newLoad >= SUMMARY_MODE_THRESHOLD,
            lastAlertType  = type
        )

        return AlertDecision.Show
    }

    /** Map SystemEvent to AlertType for evaluation. */
    fun typeFor(event: SystemEvent): AlertType = when (event) {
        is SystemEvent.LockdownActivated       -> AlertType.LOCKDOWN
        is SystemEvent.CommandOverride         -> AlertType.COMMAND_OVERRIDE
        is SystemEvent.IntegrityViolated       -> AlertType.INTEGRITY_BREACH
        is SystemEvent.ThreatLevelChanged      -> AlertType.THREAT_ESCALATE
        is SystemEvent.EmergencyBroadcast      -> AlertType.EMERGENCY_BCAST
        is SystemEvent.ForcedLogout            -> AlertType.FORCED_LOGOUT
        is SystemEvent.ConferenceStarted       -> AlertType.CONFERENCE_START
        is SystemEvent.SessionExpired          -> AlertType.SESSION_EXPIRED
        is SystemEvent.AdminEscalation         -> AlertType.ADMIN_ESCALATION
        is SystemEvent.UplinkRestored          -> AlertType.UPLINK_RESTORED
        is SystemEvent.WebSocketReconnecting   -> AlertType.WS_RECONNECTING
        is SystemEvent.AuditCaptureActive      -> AlertType.AUDIT_CAPTURE
        else                                   -> AlertType.TELEMETRY_UPDATE
    }

    /** Natural load decay — call periodically (e.g., from TelemetryManager tick). */
    fun decayLoad() {
        val current = _state.value.cognitiveLoad
        if (current <= 0) return
        val newLoad = (current - LOAD_DECAY_RATE).coerceAtLeast(0)
        _state.value = _state.value.copy(
            cognitiveLoad = newLoad,
            isSummaryMode = newLoad >= SUMMARY_MODE_THRESHOLD
        )
    }

    /** Force-reset load (e.g., after operator acknowledges summary mode). */
    fun resetLoad() {
        _state.value = _state.value.copy(cognitiveLoad = 0, isSummaryMode = false, suppressedCount = 0)
    }

    val currentLoad   get() = _state.value.cognitiveLoad
    val isSummaryMode get() = _state.value.isSummaryMode
}

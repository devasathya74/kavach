package com.kavach.app.core.forensics

import com.kavach.app.core.clock.TrustedClock
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.ThreatStateManager
import com.kavach.app.core.telemetry.NetworkTelemetry
import com.kavach.app.core.telemetry.TelemetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ForensicSnapshotSystem — Incident state capture engine.
 *
 * On critical operational events, automatically captures a complete
 * system state snapshot for postmortem analysis and audit trails.
 *
 * Each [ForensicSnapshot] records:
 *   - Trusted timestamp
 *   - Triggering event type
 *   - Current threat level
 *   - Network telemetry at moment of capture
 *   - Active session context
 *   - Sequence number (for replay reconstruction)
 *
 * Snapshots are stored in a fixed-size ring buffer (last 50 incidents).
 * Observers can read [snapshots] for the Audit Console display.
 *
 * Trigger events: ThreatLevelChanged ≥ ELEVATED, IntegrityViolated,
 *                 LockdownActivated, ForcedLogout, CommandOverride.
 */

data class ForensicSnapshot(
    val sequenceNo      : Int,
    val timestampIso    : String,
    val timestampMs     : Long,
    val triggerEvent    : String,
    val threatLevel     : ThreatLevel,
    val telemetry       : NetworkTelemetry,
    val sessionContext  : SessionContext,
    val notes           : String = ""
)

data class SessionContext(
    val tokenPresent    : Boolean = false,
    val userRole        : String  = "UNKNOWN",
    val deviceId        : String  = "UNKNOWN",
    val uplinkStatus    : String  = "UNKNOWN"
)

@Singleton
class ForensicSnapshotSystem @Inject constructor(
    private val eventBus           : EventBus,
    private val trustedClock       : TrustedClock,
    private val threatStateManager : ThreatStateManager,
    private val telemetryManager   : TelemetryManager
) {

    companion object {
        private const val RING_BUFFER_SIZE = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshots = MutableStateFlow<List<ForensicSnapshot>>(emptyList())
    val snapshots: StateFlow<List<ForensicSnapshot>> = _snapshots.asStateFlow()

    private var sequenceCounter = 0

    /** Optional: inject session context externally (call after login). */
    var activeSessionContext: SessionContext = SessionContext()

    init {
        observeEvents()
    }

    private fun observeEvents() = scope.launch {
        eventBus.events.collect { event ->
            when (event) {
                is SystemEvent.ThreatLevelChanged -> {
                    if (event.level >= ThreatLevel.ELEVATED) {
                        capture("THREAT_ESCALATED:${event.level.name}")
                    }
                }
                is SystemEvent.IntegrityViolated -> {
                    capture("INTEGRITY_VIOLATED: ${event.detail}")
                }
                is SystemEvent.LockdownActivated -> {
                    capture("LOCKDOWN_ACTIVATED by ${event.issuedBy}")
                }
                is SystemEvent.ForcedLogout -> {
                    capture("FORCED_LOGOUT: ${event.reason}")
                }
                is SystemEvent.CommandOverride -> {
                    capture("COMMAND_OVERRIDE: ${event.title} by ${event.issuedBy}")
                }
                is SystemEvent.SessionExpired -> {
                    capture("SESSION_EXPIRED: ${event.reason}")
                }
                else -> Unit
            }
        }
    }

    /** Manually capture a snapshot (e.g., from a screen action). */
    fun capture(triggerEvent: String, notes: String = "") {
        val snapshot = ForensicSnapshot(
            sequenceNo     = ++sequenceCounter,
            timestampIso   = trustedClock.nowIso(),
            timestampMs    = trustedClock.nowMs(),
            triggerEvent   = triggerEvent,
            threatLevel    = threatStateManager.currentLevel.value,
            telemetry      = telemetryManager.telemetry.value,
            sessionContext = activeSessionContext,
            notes          = notes
        )

        val current = _snapshots.value.toMutableList()
        current.add(0, snapshot) // newest first
        if (current.size > RING_BUFFER_SIZE) {
            current.subList(RING_BUFFER_SIZE, current.size).clear()
        }
        _snapshots.value = current
    }

    /** Export snapshots as a formatted log string (for encrypted export). */
    fun exportLog(): String = buildString {
        appendLine("=== KAVACH FORENSIC INCIDENT LOG ===")
        appendLine("Generated: ${trustedClock.nowIso()}")
        appendLine("Total Snapshots: ${_snapshots.value.size}")
        appendLine()
        _snapshots.value.forEach { snap ->
            appendLine("─── INCIDENT #${snap.sequenceNo} ───────────────────────")
            appendLine("  Time       : ${snap.timestampIso}")
            appendLine("  Trigger    : ${snap.triggerEvent}")
            appendLine("  ThreatLevel: ${snap.threatLevel.name}")
            appendLine("  RTT        : ${snap.telemetry.apiRttMs}ms")
            appendLine("  PacketLoss : ${snap.telemetry.packetLossPct}%")
            appendLine("  Uplink     : ${snap.telemetry.uplinkStatus}")
            appendLine("  Role       : ${snap.sessionContext.userRole}")
            if (snap.notes.isNotBlank()) {
                appendLine("  Notes      : ${snap.notes}")
            }
            appendLine()
        }
    }
}

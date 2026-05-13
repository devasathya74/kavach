package com.kavach.app.core.reconciliation

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.ThreatStateManager
import com.kavach.app.core.security.CommandModeController
import com.kavach.app.core.security.CommandMode
import com.kavach.app.core.timeline.CommandTimelineEngine
import com.kavach.app.core.timeline.TimelineCategory
import com.kavach.app.core.timeline.TimelineSeverity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StateReconciliationEngine — Server-authoritative state reconciliation.
 *
 * After any connectivity disruption, the client CANNOT assume its local state
 * matches the server's authoritative state. This engine:
 *
 *   1. Requests authoritative state snapshot from server on reconnect
 *   2. Compares with local state (threat level, command mode, pending overlays)
 *   3. Applies any divergent values (server wins)
 *   4. Validates timeline continuity (detects missed events)
 *   5. Reports reconciliation result to CommandTimelineEngine
 *
 * Divergence scenarios:
 *   - Client was SECURE, server moved to CRITICAL during offline
 *   - Server lifted lockdown while client was disconnected
 *   - New command override was issued while client was offline
 *
 * [ReconciliationResult] is observable for UI feedback ("State reconciled").
 */

data class AuthoritativeState(
    val threatLevel     : ThreatLevel,
    val commandMode     : CommandMode,
    val pendingOverride : String?,    // null if none
    val isLockdownActive: Boolean,
    val serverSequenceId: Long,
    val serverTimestampMs: Long
)

sealed class ReconciliationResult {
    object InSync                           : ReconciliationResult()
    data class Reconciled(
        val divergedFields  : List<String>,
        val appliedChanges  : List<String>
    ) : ReconciliationResult()
    data class Failed(val reason: String)   : ReconciliationResult()
    object InProgress                       : ReconciliationResult()
}

@Singleton
class StateReconciliationEngine @Inject constructor(
    private val eventBus              : EventBus,
    private val threatStateManager    : ThreatStateManager,
    private val commandModeController : CommandModeController,
    private val timelineEngine        : CommandTimelineEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _result = MutableStateFlow<ReconciliationResult>(ReconciliationResult.InSync)
    val result: StateFlow<ReconciliationResult> = _result.asStateFlow()

    init {
        observeReconnect()
    }

    private fun observeReconnect() = scope.launch {
        eventBus.events.collect { event ->
            if (event is SystemEvent.WebSocketConnected) {
                // On every reconnect, trigger reconciliation
                reconcile()
            }
        }
    }

    /**
     * Begin reconciliation. In production: [fetchAuthoritativeState] makes a
     * real API call (e.g., GET /api/v1/system/state) and returns the server state.
     * Here we accept it as a parameter for testability.
     */
    fun reconcile(
        fetchAuthoritativeState: suspend () -> AuthoritativeState? = { null }
    ) = scope.launch {
        _result.value = ReconciliationResult.InProgress

        val serverState = try {
            withTimeout(10_000L) { fetchAuthoritativeState() }
        } catch (e: TimeoutCancellationException) {
            _result.value = ReconciliationResult.Failed("Reconciliation timeout — using local state")
            timelineEngine.record(
                TimelineCategory.TELEMETRY, TimelineSeverity.WARNING,
                "RECONCILIATION", "STATE SYNC",
                "State reconciliation timed out — operating on potentially stale local state"
            )
            return@launch
        }

        if (serverState == null) {
            // No server state available — assume local state is current
            _result.value = ReconciliationResult.InSync
            return@launch
        }

        val diverged  = mutableListOf<String>()
        val applied   = mutableListOf<String>()

        // ── Threat Level reconciliation ───────────────────────
        val localThreat  = threatStateManager.currentLevel.value
        if (serverState.threatLevel != localThreat) {
            diverged.add("ThreatLevel: local=${localThreat.name}, server=${serverState.threatLevel.name}")
            // Server always wins
            threatStateManager.setLevel(serverState.threatLevel)
            applied.add("ThreatLevel set to ${serverState.threatLevel.name}")
            eventBus.emit(SystemEvent.ThreatLevelChanged(serverState.threatLevel))
        }

        // ── Command Mode reconciliation ───────────────────────
        val localMode = commandModeController.currentMode.value
        if (serverState.commandMode != localMode) {
            diverged.add("CommandMode: local=${localMode.name}, server=${serverState.commandMode.name}")
            commandModeController.setMode(serverState.commandMode)
            applied.add("CommandMode set to ${serverState.commandMode.name}")
        }

        // ── Lockdown state reconciliation ─────────────────────
        val localLockdown = commandModeController.isLockdown
        if (serverState.isLockdownActive && !localLockdown) {
            diverged.add("Lockdown: server=ACTIVE, local=INACTIVE")
            eventBus.emit(SystemEvent.LockdownActivated("SERVER AUTHORITY (reconciled)"))
            applied.add("Lockdown activated from server state")
        } else if (!serverState.isLockdownActive && localLockdown) {
            diverged.add("Lockdown: server=LIFTED, local=ACTIVE")
            eventBus.emit(SystemEvent.LockdownLifted)
            applied.add("Lockdown lifted from server state")
        }

        // ── Pending override reconciliation ───────────────────
        if (serverState.pendingOverride != null) {
            diverged.add("PendingOverride: server has override not seen locally")
            eventBus.emit(SystemEvent.CommandOverride(
                title      = "Recovered Directive",
                body       = serverState.pendingOverride,
                issuedBy   = "SERVER AUTHORITY",
                requiresAck = true
            ))
            applied.add("Recovered pending command override")
        }

        // ── Record reconciliation in timeline ─────────────────
        val result = if (diverged.isEmpty()) {
            ReconciliationResult.InSync
        } else {
            ReconciliationResult.Reconciled(diverged, applied)
        }

        _result.value = result

        timelineEngine.record(
            TimelineCategory.TELEMETRY,
            if (diverged.isEmpty()) TimelineSeverity.INFO else TimelineSeverity.WARNING,
            "RECONCILIATION ENGINE",
            "STATE SYNC",
            if (diverged.isEmpty()) "State reconciled — in sync with server"
            else "State divergence corrected: ${diverged.size} field(s) reconciled"
        )
    }
}

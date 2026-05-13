package com.kavach.app.core.policy

import com.kavach.app.core.security.CommandMode
import com.kavach.app.core.security.CommandModeController
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.ThreatStateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PolicyEngine — Dynamic operational policy evaluation.
 *
 * Provides a centralized, queryable rule set that determines what
 * actions and features are permitted given the current system state
 * (ThreatLevel + CommandMode + user role).
 *
 * All gating decisions throughout the app must flow through this engine.
 * No hardcoded if/else permission checks in UI components.
 *
 * Usage:
 *   val policy = policyEngine.evaluate(PolicyContext(...))
 *   if (!policy.allowsDownload) { /* block */ }
 */

// ── Policy Context — snapshot of evaluation inputs ───────────

data class PolicyContext(
    val threatLevel  : ThreatLevel,
    val commandMode  : CommandMode,
    val userRole     : String,
    val isOnline     : Boolean    = true,
    val sessionValid : Boolean    = true
)

// ── Policy Decision — what is permitted ──────────────────────

data class PolicyDecision(
    val allowDownload        : Boolean,
    val allowScreenCapture   : Boolean,
    val allowConference      : Boolean,
    val allowOrderExport     : Boolean,
    val allowPersonnelEdit   : Boolean,
    val allowBroadcast       : Boolean,
    val requireReAuth        : Boolean,
    val showThreatOverlay    : Boolean,
    val telemetryDensity     : TelemetryDensity,
    val blockedReason        : String?
)

enum class TelemetryDensity { MINIMAL, STANDARD, AGGRESSIVE, MAXIMUM }

// ── Engine ────────────────────────────────────────────────────

@Singleton
class PolicyEngine @Inject constructor(
    private val threatStateManager  : ThreatStateManager,
    private val commandModeController: CommandModeController
) {

    /**
     * Evaluate the current policy given a context snapshot.
     * Returns a [PolicyDecision] that callers use to gate UI/actions.
     */
    fun evaluate(context: PolicyContext = currentContext()): PolicyDecision {
        val threat  = context.threatLevel
        val mode    = context.commandMode
        val role    = context.userRole.uppercase()
        val online  = context.isOnline
        val session = context.sessionValid

        // Short-circuit: no valid session → block everything
        if (!session) return PolicyDecision(
            allowDownload      = false,
            allowScreenCapture = false,
            allowConference    = false,
            allowOrderExport   = false,
            allowPersonnelEdit = false,
            allowBroadcast     = false,
            requireReAuth      = true,
            showThreatOverlay  = false,
            telemetryDensity   = TelemetryDensity.MINIMAL,
            blockedReason      = "Session invalid — re-authentication required"
        )

        return PolicyDecision(
            // Downloads: blocked at EMERGENCY or above, and in lockdown
            allowDownload = mode.allowDownloads && threat < ThreatLevel.ELEVATED,

            // Screen capture: always blocked (FLAG_SECURE enforced globally)
            allowScreenCapture = false,

            // Conference: blocked only in LOCKDOWN
            allowConference = mode != CommandMode.LOCKDOWN && online,

            // Order export: blocked at CRITICAL or above
            allowOrderExport = threat < ThreatLevel.CRITICAL && mode.allowDownloads,

            // Personnel edit: requires PILOT or above, blocked in lockdown
            allowPersonnelEdit = role in listOf("PILOT","ADMIN","COMMANDING_OFFICER","SUPERUSER")
                && mode != CommandMode.LOCKDOWN,

            // Broadcast: requires ADMIN or above
            allowBroadcast = role in listOf("ADMIN","COMMANDING_OFFICER","SUPERUSER")
                && session,

            // Re-auth: required in EMERGENCY_COMMAND and LOCKDOWN
            requireReAuth = mode.requiresReAuth || threat >= ThreatLevel.CRITICAL,

            // Threat overlay: shown at WARNING and above
            showThreatOverlay = threat >= ThreatLevel.WARNING,

            // Telemetry density scales with mode
            telemetryDensity = when (mode) {
                CommandMode.NORMAL              -> TelemetryDensity.STANDARD
                CommandMode.ELEVATED_MONITORING -> TelemetryDensity.AGGRESSIVE
                CommandMode.EMERGENCY_COMMAND   -> TelemetryDensity.MAXIMUM
                CommandMode.LOCKDOWN            -> TelemetryDensity.MAXIMUM
            },

            blockedReason = when {
                mode == CommandMode.LOCKDOWN -> "LOCKDOWN PROTOCOL — All non-essential operations suspended"
                !online                      -> "Uplink offline — limited operation mode"
                threat >= ThreatLevel.CRITICAL -> "CRITICAL threat level — operations restricted"
                else                          -> null
            }
        )
    }

    /** Convenience: evaluate against live system state. */
    fun currentContext(): PolicyContext = PolicyContext(
        threatLevel = threatStateManager.currentLevel.value,
        commandMode = commandModeController.currentMode.value,
        userRole    = "USER" // Caller should override with actual role
    )

    /** Quick checks — avoid instantiating PolicyDecision for simple gates. */
    fun canDownload(role: String) = evaluate(currentContext().copy(userRole = role)).allowDownload
    fun canBroadcast(role: String) = evaluate(currentContext().copy(userRole = role)).allowBroadcast
    fun canEditPersonnel(role: String) = evaluate(currentContext().copy(userRole = role)).allowPersonnelEdit
    fun requiresReAuth(): Boolean = evaluate().requireReAuth
}

package com.kavach.app.core.events

import com.kavach.app.core.security.ThreatLevel

/**
 * SystemEvent — Sealed hierarchy of all operational events
 * that can propagate through the global EventBus.
 *
 * Keep this exhaustive. Every cross-screen signal must go here.
 */
sealed class SystemEvent {

    // ── Authentication & Session ─────────────────────────────
    data class SessionExpired(val reason: String)            : SystemEvent()
    data class ForcedLogout(val reason: String)              : SystemEvent()
    object SessionRestored                                   : SystemEvent()

    // ── Threat State ─────────────────────────────────────────
    data class ThreatLevelChanged(val level: ThreatLevel)    : SystemEvent()
    data class IntegrityViolated(val detail: String)         : SystemEvent()
    object ThreatCleared                                     : SystemEvent()

    // ── Command Interruption (highest priority) ───────────────
    data class CommandOverride(
        val title       : String,
        val body        : String,
        val issuedBy    : String,
        val requiresAck : Boolean = true
    ) : SystemEvent()

    data class LockdownActivated(val issuedBy: String)       : SystemEvent()
    object LockdownLifted                                    : SystemEvent()

    // ── Conference & Live Broadcast ──────────────────────────
    data class ConferenceStarted(
        val conferenceId : String,
        val title        : String,
        val hostName     : String
    ) : SystemEvent()
    data class ConferenceEnded(val conferenceId: String)     : SystemEvent()
    data class EmergencyBroadcast(
        val broadcastId : String,
        val message     : String,
        val priority    : String
    ) : SystemEvent()

    // ── Connectivity & Telemetry ─────────────────────────────
    object UplinkDegraded                                    : SystemEvent()
    object UplinkRestored                                    : SystemEvent()
    data class WebSocketReconnecting(val attempt: Int)       : SystemEvent()
    object WebSocketConnected                                : SystemEvent()

    // ── Audit ────────────────────────────────────────────────
    object AuditCaptureActive                                : SystemEvent()
    data class AdminEscalation(val targetPno: String, val reason: String) : SystemEvent()

    // ── Deployment ───────────────────────────────────────────
    data class DeploymentFreeze(val reason: String)          : SystemEvent()
    object DeploymentResumed                                 : SystemEvent()

    // ── Training ─────────────────────────────────────────────
    data class SuspiciousQuizBehavior(val pno: String)       : SystemEvent()
    data class TrainingCompleted(val pno: String, val moduleId: String) : SystemEvent()
}

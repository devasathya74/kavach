package com.kavach.app.core.timeline

import com.kavach.app.core.clock.TrustedClock
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
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
 * CommandTimelineEngine — Chronological operational reconstruction.
 *
 * Maintains a live timeline of all significant operational events.
 * This IS the institutional memory — every command, escalation,
 * broadcast, disconnect spike, and threat change is recorded here.
 *
 * Displayed in the Audit Console for investigators and command staff.
 * Ring buffer: last [TIMELINE_SIZE] events (300 max).
 *
 * Each [TimelineEntry] has:
 *   - Trusted timestamp
 *   - Event category (for filtering)
 *   - Actor (who caused it) + subject (who/what was affected)
 *   - Severity level (for color coding in UI)
 *   - Detail string
 */

// ── Timeline Entry ────────────────────────────────────────────

enum class TimelineCategory {
    COMMAND,        // Overrides, lockdowns, directives
    THREAT,         // Threat level changes, integrity violations
    BROADCAST,      // Emergency or normal broadcasts
    CONFERENCE,     // Conference start/end
    SESSION,        // Login, logout, expiry, forced logout
    TELEMETRY,      // Reconnect, uplink events
    TRAINING,       // Quiz suspicion, completions
    AUDIT,          // Explicit audit capture events
    DEPLOYMENT      // OTA freeze, resume
}

enum class TimelineSeverity { INFO, NOTICE, WARNING, CRITICAL }

data class TimelineEntry(
    val id          : Int,
    val timestampIso: String,
    val timestampMs : Long,
    val category    : TimelineCategory,
    val severity    : TimelineSeverity,
    val actor       : String,
    val subject     : String,
    val detail      : String
)

// ── Engine ────────────────────────────────────────────────────

@Singleton
class CommandTimelineEngine @Inject constructor(
    private val eventBus    : EventBus,
    private val trustedClock: TrustedClock
) {

    companion object {
        private const val TIMELINE_SIZE = 300
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _timeline = MutableStateFlow<List<TimelineEntry>>(emptyList())
    val timeline: StateFlow<List<TimelineEntry>> = _timeline.asStateFlow()

    private var entryCounter = 0

    init {
        observeEventBus()
    }

    private fun observeEventBus() = scope.launch {
        eventBus.events.collect { event ->
            val entry = mapEventToEntry(event) ?: return@collect
            append(entry)
        }
    }

    private fun mapEventToEntry(event: SystemEvent): TimelineEntry? = when (event) {

        is SystemEvent.ThreatLevelChanged -> entry(
            category = TimelineCategory.THREAT,
            severity = if (event.level.isCritical) TimelineSeverity.CRITICAL else TimelineSeverity.WARNING,
            actor    = "SYSTEM",
            subject  = "GLOBAL THREAT STATE",
            detail   = "Threat level changed → ${event.level.name}"
        )

        is SystemEvent.IntegrityViolated -> entry(
            category = TimelineCategory.THREAT,
            severity = TimelineSeverity.CRITICAL,
            actor    = "INTEGRITY ENGINE",
            subject  = "ENVIRONMENT",
            detail   = event.detail
        )

        is SystemEvent.LockdownActivated -> entry(
            category = TimelineCategory.COMMAND,
            severity = TimelineSeverity.CRITICAL,
            actor    = event.issuedBy,
            subject  = "ALL UNITS",
            detail   = "LOCKDOWN PROTOCOL ACTIVATED"
        )

        is SystemEvent.LockdownLifted -> entry(
            category = TimelineCategory.COMMAND,
            severity = TimelineSeverity.NOTICE,
            actor    = "COMMAND AUTHORITY",
            subject  = "ALL UNITS",
            detail   = "Lockdown protocol lifted — normal operation resumed"
        )

        is SystemEvent.CommandOverride -> entry(
            category = TimelineCategory.COMMAND,
            severity = TimelineSeverity.CRITICAL,
            actor    = event.issuedBy,
            subject  = "ALL UNITS",
            detail   = "COMMAND OVERRIDE: ${event.title}"
        )

        is SystemEvent.EmergencyBroadcast -> entry(
            category = TimelineCategory.BROADCAST,
            severity = TimelineSeverity.CRITICAL,
            actor    = "BROADCAST SYSTEM",
            subject  = event.broadcastId,
            detail   = "[${event.priority}] ${event.message}"
        )

        is SystemEvent.ConferenceStarted -> entry(
            category = TimelineCategory.CONFERENCE,
            severity = TimelineSeverity.INFO,
            actor    = event.hostName,
            subject  = event.conferenceId,
            detail   = "Conference started: ${event.title}"
        )

        is SystemEvent.ConferenceEnded -> entry(
            category = TimelineCategory.CONFERENCE,
            severity = TimelineSeverity.INFO,
            actor    = "SYSTEM",
            subject  = event.conferenceId,
            detail   = "Conference ended"
        )

        is SystemEvent.ForcedLogout -> entry(
            category = TimelineCategory.SESSION,
            severity = TimelineSeverity.WARNING,
            actor    = "SYSTEM",
            subject  = "SESSION",
            detail   = "FORCED LOGOUT: ${event.reason}"
        )

        is SystemEvent.SessionExpired -> entry(
            category = TimelineCategory.SESSION,
            severity = TimelineSeverity.NOTICE,
            actor    = "SESSION MANAGER",
            subject  = "TOKEN",
            detail   = "Session expired: ${event.reason}"
        )

        is SystemEvent.WebSocketReconnecting -> entry(
            category = TimelineCategory.TELEMETRY,
            severity = TimelineSeverity.WARNING,
            actor    = "WS ENGINE",
            subject  = "UPLINK",
            detail   = "WebSocket reconnecting — attempt #${event.attempt}"
        )

        is SystemEvent.WebSocketConnected -> entry(
            category = TimelineCategory.TELEMETRY,
            severity = TimelineSeverity.INFO,
            actor    = "WS ENGINE",
            subject  = "UPLINK",
            detail   = "WebSocket connection established"
        )

        is SystemEvent.UplinkDegraded -> entry(
            category = TimelineCategory.TELEMETRY,
            severity = TimelineSeverity.WARNING,
            actor    = "TELEMETRY",
            subject  = "UPLINK",
            detail   = "Uplink degraded — limited operation mode active"
        )

        is SystemEvent.SuspiciousQuizBehavior -> entry(
            category = TimelineCategory.TRAINING,
            severity = TimelineSeverity.WARNING,
            actor    = event.pno,
            subject  = "QUIZ SYSTEM",
            detail   = "Suspicious assessment behavior detected for ${event.pno}"
        )

        is SystemEvent.AdminEscalation -> entry(
            category = TimelineCategory.AUDIT,
            severity = TimelineSeverity.WARNING,
            actor    = "ADMIN",
            subject  = event.targetPno,
            detail   = event.reason
        )

        is SystemEvent.DeploymentFreeze -> entry(
            category = TimelineCategory.DEPLOYMENT,
            severity = TimelineSeverity.CRITICAL,
            actor    = "COMMAND",
            subject  = "DEPLOYMENT SYSTEM",
            detail   = "DEPLOYMENT FROZEN: ${event.reason}"
        )

        else -> null // Non-timeline events are filtered
    }

    /** Manually append a custom entry (e.g., from Audit Console). */
    fun record(
        category : TimelineCategory,
        severity : TimelineSeverity,
        actor    : String,
        subject  : String,
        detail   : String
    ) = append(entry(category, severity, actor, subject, detail))

    private fun entry(
        category : TimelineCategory,
        severity : TimelineSeverity,
        actor    : String,
        subject  : String,
        detail   : String
    ) = TimelineEntry(
        id           = ++entryCounter,
        timestampIso = trustedClock.nowIso(),
        timestampMs  = trustedClock.nowMs(),
        category     = category,
        severity     = severity,
        actor        = actor,
        subject      = subject,
        detail       = detail
    )

    private fun append(entry: TimelineEntry) {
        val current = _timeline.value.toMutableList()
        current.add(0, entry) // newest first
        if (current.size > TIMELINE_SIZE) {
            current.subList(TIMELINE_SIZE, current.size).clear()
        }
        _timeline.value = current
    }

    /** Filter helpers for the Audit Console. */
    fun byCategory(cat: TimelineCategory)   = _timeline.value.filter { it.category == cat }
    fun bySeverity(sev: TimelineSeverity)   = _timeline.value.filter { it.severity == sev }
    fun byActor(actor: String)              = _timeline.value.filter { it.actor.contains(actor, ignoreCase = true) }
    fun since(epochMs: Long)                = _timeline.value.filter { it.timestampMs >= epochMs }
}

package com.kavach.app.core.security

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CommandModeController — Operational UI mode state machine.
 *
 * Switches the entire app into one of four command modes, each of which:
 *   - Changes overlay density and telemetry aggression
 *   - Adjusts sound behavior
 *   - Controls module availability
 *   - Modifies interruption thresholds
 *
 * Mode transitions are one-directional through ThreatLevel linkage:
 *   NORMAL → ELEVATED_MONITORING → EMERGENCY_COMMAND → LOCKDOWN
 * De-escalation requires explicit command authority (never automatic).
 *
 * Observers: OverlaySystem, TelemetryManager, PolicyEngine, SecureScreenController.
 */

enum class CommandMode(
    val label              : String,
    val telemetryIntervalMs: Long,    // How aggressively telemetry polls
    val overlayDensity     : Float,   // 0.0 = minimal, 1.0 = maximum
    val soundEnabled       : Boolean,
    val allowDownloads     : Boolean,
    val allowScreenshots   : Boolean,
    val requiresReAuth     : Boolean,
    val navigationBlocked  : Boolean
) {
    NORMAL(
        label               = "NORMAL OPERATION",
        telemetryIntervalMs = 5_000L,
        overlayDensity      = 0f,
        soundEnabled        = true,
        allowDownloads      = true,
        allowScreenshots    = false, // FLAG_SECURE always on
        requiresReAuth      = false,
        navigationBlocked   = false
    ),
    ELEVATED_MONITORING(
        label               = "ELEVATED MONITORING",
        telemetryIntervalMs = 2_000L,
        overlayDensity      = 0.4f,
        soundEnabled        = true,
        allowDownloads      = true,
        allowScreenshots    = false,
        requiresReAuth      = false,
        navigationBlocked   = false
    ),
    EMERGENCY_COMMAND(
        label               = "EMERGENCY COMMAND",
        telemetryIntervalMs = 1_000L,
        overlayDensity      = 0.8f,
        soundEnabled        = true,
        allowDownloads      = false,
        allowScreenshots    = false,
        requiresReAuth      = true,
        navigationBlocked   = false
    ),
    LOCKDOWN(
        label               = "LOCKDOWN PROTOCOL",
        telemetryIntervalMs = 500L,
        overlayDensity      = 1.0f,
        soundEnabled        = true,
        allowDownloads      = false,
        allowScreenshots    = false,
        requiresReAuth      = true,
        navigationBlocked   = true
    );

    /** Map threat level to the appropriate command mode. */
    companion object {
        fun fromThreatLevel(level: ThreatLevel): CommandMode = when (level) {
            ThreatLevel.SECURE      -> NORMAL
            ThreatLevel.WARNING     -> NORMAL
            ThreatLevel.ELEVATED    -> ELEVATED_MONITORING
            ThreatLevel.CRITICAL    -> EMERGENCY_COMMAND
            ThreatLevel.COMPROMISED -> LOCKDOWN
        }
    }
}

@Singleton
class CommandModeController @Inject constructor(
    private val eventBus             : EventBus,
    private val threatStateManager   : ThreatStateManager,
    private val secureScreenController: SecureScreenController
) {

    private val _currentMode = MutableStateFlow(CommandMode.NORMAL)
    val currentMode: StateFlow<CommandMode> = _currentMode.asStateFlow()

    /**
     * Evaluate the current threat level and auto-transition mode.
     * Call this from ThreatStateManager whenever level changes.
     */
    fun syncWithThreatLevel(level: ThreatLevel) {
        val targetMode = CommandMode.fromThreatLevel(level)
        // Only escalate — never auto-de-escalate
        if (targetMode.ordinal > _currentMode.value.ordinal) {
            setMode(targetMode)
        }
    }

    /** Manually set command mode (requires authority). */
    fun setMode(mode: CommandMode) {
        val previous = _currentMode.value
        if (previous == mode) return
        _currentMode.value = mode
        secureScreenController.onCommandModeChanged(mode)

        // Propagate lockdown/unlock events
        when (mode) {
            CommandMode.LOCKDOWN -> eventBus.emit(SystemEvent.LockdownActivated("COMMAND AUTHORITY"))
            else -> if (previous == CommandMode.LOCKDOWN) {
                eventBus.emit(SystemEvent.LockdownLifted)
            }
        }
    }

    /** De-escalate one step — requires explicit authority. */
    fun deEscalate() {
        val prev = _currentMode.value
        val next = CommandMode.entries.getOrNull(prev.ordinal - 1) ?: CommandMode.NORMAL
        setMode(next)
        // Also de-escalate threat state
        threatStateManager.setLevel(ThreatLevel.entries.getOrElse(next.ordinal) { ThreatLevel.SECURE })
    }

    /** Full clear — only Senanayak authority. */
    fun clearToNormal() {
        setMode(CommandMode.NORMAL)
        threatStateManager.clear()
    }

    val isLockdown      get() = _currentMode.value == CommandMode.LOCKDOWN
    val isEmergency     get() = _currentMode.value >= CommandMode.EMERGENCY_COMMAND
    val isElevated      get() = _currentMode.value >= CommandMode.ELEVATED_MONITORING
}

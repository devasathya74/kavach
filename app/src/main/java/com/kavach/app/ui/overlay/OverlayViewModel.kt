package com.kavach.app.ui.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.core.attention.AlertDecision
import com.kavach.app.core.attention.AlertType
import com.kavach.app.core.attention.AttentionBudget
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.perf.PerformanceMonitor
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.ThreatStateManager
import com.kavach.app.core.sound.SoundManager
import com.kavach.app.core.sound.TacticalSound
import com.kavach.app.core.telemetry.TelemetryManager
import com.kavach.app.core.telemetry.UplinkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OverlayViewModel — Central controller for the TacticalOverlaySystem.
 *
 * Observes EventBus and ThreatStateManager, then surfaces overlay state
 * that KavachNavHost can pass into [TacticalOverlaySystem].
 *
 * AttentionBudget gates all suppressible alerts:
 *   - LOCKDOWN / COMMAND_OVERRIDE: always shown (non-suppressible)
 *   - EMERGENCY_BCAST / THREAT_ESCALATE: shown unless cognitive load >= 75
 *   - UPLINK_RESTORED / WS_RECONNECTING: 30-60s cooldown, suppressible
 */
@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val eventBus           : EventBus,
    private val threatStateManager : ThreatStateManager,
    private val telemetryManager   : TelemetryManager,
    private val soundManager       : SoundManager,
    private val attentionBudget    : AttentionBudget,
    private val performanceMonitor : PerformanceMonitor
) : ViewModel() {

    // -- Threat Level --
    val threatLevel: StateFlow<ThreatLevel> = threatStateManager.currentLevel

    // -- Uplink Degraded --
    val uplinkDegraded: StateFlow<Boolean> = telemetryManager.telemetry
        .map { it.uplinkStatus != UplinkStatus.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // -- Command Interruption --
    private val _pendingCommand = MutableStateFlow<SystemEvent.CommandOverride?>(null)
    val pendingCommand: StateFlow<SystemEvent.CommandOverride?> = _pendingCommand.asStateFlow()

    // -- Lockdown --
    private val _isLockdown = MutableStateFlow(false)
    val isLockdown: StateFlow<Boolean> = _isLockdown.asStateFlow()

    // -- Emergency Broadcast --
    private val _emergencyBroadcast = MutableStateFlow<SystemEvent.EmergencyBroadcast?>(null)
    val emergencyBroadcast: StateFlow<SystemEvent.EmergencyBroadcast?> = _emergencyBroadcast.asStateFlow()

    // -- Attention Budget state exposed for admin diagnostics --
    val attentionState = attentionBudget.state

    init {
        observeEvents()
        telemetryManager.start()
    }

    private fun observeEvents() = viewModelScope.launch {
        eventBus.events.collect { event ->
            when (event) {

                // CRITICAL: Never suppressed by AttentionBudget
                is SystemEvent.LockdownActivated -> {
                    attentionBudget.evaluate(AlertType.LOCKDOWN) // cost=20, non-suppressible
                    _isLockdown.value = true
                    threatStateManager.escalateTo(ThreatLevel.CRITICAL)
                    soundManager.play(TacticalSound.THREAT_WARNING)
                    performanceMonitor.onOverlayShown()
                }

                is SystemEvent.CommandOverride -> {
                    attentionBudget.evaluate(AlertType.COMMAND_OVERRIDE) // cost=18, non-suppressible
                    _pendingCommand.value = event
                    soundManager.play(TacticalSound.ESCALATION_TONE)
                    performanceMonitor.onOverlayShown()
                }

                // SUPPRESSIBLE: Checked against cognitive load
                is SystemEvent.EmergencyBroadcast -> {
                    val decision = attentionBudget.evaluate(AlertType.EMERGENCY_BCAST)
                    if (decision is AlertDecision.Show) {
                        _emergencyBroadcast.value = event
                        soundManager.play(TacticalSound.EMERGENCY_BROADCAST)
                    }
                }

                is SystemEvent.ThreatLevelChanged -> {
                    val decision = attentionBudget.evaluate(AlertType.THREAT_ESCALATE)
                    if (decision is AlertDecision.Show) {
                        when (event.level) {
                            ThreatLevel.WARNING     -> soundManager.play(TacticalSound.ALERT_PING, 0.25f)
                            ThreatLevel.ELEVATED,
                            ThreatLevel.CRITICAL    -> soundManager.play(TacticalSound.ESCALATION_TONE)
                            ThreatLevel.COMPROMISED -> soundManager.play(TacticalSound.THREAT_WARNING)
                            else                    -> Unit
                        }
                    }
                }

                is SystemEvent.WebSocketConnected -> {
                    val decision = attentionBudget.evaluate(AlertType.UPLINK_RESTORED)
                    if (decision is AlertDecision.Show) {
                        soundManager.play(TacticalSound.UPLINK_CONNECT, 0.2f)
                    }
                }

                is SystemEvent.LockdownLifted -> {
                    _isLockdown.value = false
                    threatStateManager.clear()
                }

                else -> Unit
            }
        }
    }

    fun acknowledgeCommand() {
        _pendingCommand.value = null
        performanceMonitor.onOverlayDismissed()
    }

    fun acknowledgeLockdown() {
        // Lockdown remains active operationally -- only the fullscreen overlay is hidden
        _isLockdown.value = false
        performanceMonitor.onOverlayDismissed()
    }

    fun dismissEmergencyBroadcast() {
        _emergencyBroadcast.value = null
    }

    fun acknowledgeAttentionSummary() {
        attentionBudget.resetLoad()
    }
}

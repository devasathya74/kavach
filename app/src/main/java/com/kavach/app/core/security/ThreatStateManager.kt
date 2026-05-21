package com.kavach.app.core.security

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ThreatStateManager — Centralized operational security state.
 *
 * Single source of truth for the app's current threat level.
 * Screens, overlays, and UI components observe [currentLevel].
 *
 * When level changes, it automatically emits a [SystemEvent.ThreatLevelChanged]
 * through the EventBus so all listeners are notified.
 *
 * Hilt-injected singleton; survives navigation.
 */
@Singleton
class ThreatStateManager @Inject constructor(
    private val eventBus: EventBus
) {

    private val _currentLevel = MutableStateFlow(ThreatLevel.SECURE)

    /** Observable current threat level. Collect in any composable or ViewModel. */
    val currentLevel: StateFlow<ThreatLevel> = _currentLevel.asStateFlow()

    /** Escalate or de-escalate the global threat state. */
    fun setLevel(level: ThreatLevel) {
        val previous = _currentLevel.value
        if (previous == level) return
        _currentLevel.value = level
        eventBus.emit(SystemEvent.ThreatLevelChanged(level))

        // Auto-emit specific high-priority events
        when (level) {
            ThreatLevel.COMPROMISED -> eventBus.emit(
                SystemEvent.IntegrityViolated("Environment classified COMPROMISED")
            )
            ThreatLevel.CRITICAL    -> eventBus.emit(
                SystemEvent.ThreatLevelChanged(ThreatLevel.CRITICAL)
            )
            ThreatLevel.SECURE      -> if (previous.isCritical) {
                eventBus.emit(SystemEvent.ThreatCleared)
            }
            else -> Unit
        }
    }

    /** Escalate only — never lowers level without explicit [setLevel] call. */
    fun escalateTo(level: ThreatLevel) {
        if (level > _currentLevel.value) setLevel(level)
    }

    /** Reset to SECURE (only call after explicit clearance). */
    fun clear() = setLevel(ThreatLevel.SECURE)

    val isBlocking  get() = _currentLevel.value.blocksNavigation
    val isCritical  get() = _currentLevel.value.isCritical
}

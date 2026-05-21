package com.kavach.app.core.degraded

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.telemetry.TelemetryManager
import com.kavach.app.core.telemetry.UplinkStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DegradedModeController — Operational confidence state engine.
 *
 * Translates raw connectivity metrics into an operator-visible
 * degraded state that the UI communicates clearly.
 *
 * The critical design principle:
 *   FULL_OPERATION ≠ "working"
 *   It means: operator can trust all data displayed.
 *
 * When confidence drops, the UI must VISIBLY communicate:
 *   "what you are seeing may not be current"
 *   "commands may not propagate immediately"
 *   "acknowledgment counts are unreliable"
 *
 * This prevents operators from acting on stale state.
 */

enum class DegradedMode(
    val label              : String,
    val confidencePct      : Int,     // Operational data confidence
    val colorHex           : Long,
    val showWarningBanner  : Boolean,
    val commandsReliable   : Boolean,
    val telemetryFresh     : Boolean,
    val syncExpected       : Boolean
) {
    FULL_OPERATION(
        label             = "FULL OPERATION",
        confidencePct     = 100,
        colorHex          = 0xFF16A34A,
        showWarningBanner = false,
        commandsReliable  = true,
        telemetryFresh    = true,
        syncExpected      = true
    ),
    UPLINK_DEGRADED(
        label             = "UPLINK DEGRADED",
        confidencePct     = 75,
        colorHex          = 0xFFF59E0B,
        showWarningBanner = true,
        commandsReliable  = true,   // Commands queue and retry
        telemetryFresh    = false,  // Telemetry may lag
        syncExpected      = false
    ),
    PARTIAL_TELEMETRY(
        label             = "PARTIAL TELEMETRY",
        confidencePct     = 55,
        colorHex          = 0xFFEA580C,
        showWarningBanner = true,
        commandsReliable  = false,  // Delivery unconfirmed
        telemetryFresh    = false,
        syncExpected      = false
    ),
    COMMAND_DESYNC_RISK(
        label             = "COMMAND DESYNC RISK",
        confidencePct     = 30,
        colorHex          = 0xFFDC2626,
        showWarningBanner = true,
        commandsReliable  = false,
        telemetryFresh    = false,
        syncExpected      = false
    ),
    ISOLATED(
        label             = "OPERATIONALLY ISOLATED",
        confidencePct     = 0,
        colorHex          = 0xFF7F1D1D,
        showWarningBanner = true,
        commandsReliable  = false,
        telemetryFresh    = false,
        syncExpected      = false
    );
}

data class DegradedState(
    val mode              : DegradedMode = DegradedMode.FULL_OPERATION,
    val reason            : String       = "",
    val lastFullSyncMs    : Long         = 0L,
    val pendingCommandsCt : Int          = 0
)

@Singleton
class DegradedModeController @Inject constructor(
    private val telemetryManager : TelemetryManager,
    private val eventBus         : EventBus
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(DegradedState())
    val state: StateFlow<DegradedState> = _state.asStateFlow()

    val currentMode get() = _state.value.mode

    init {
        observeTelemetry()
        observeEvents()
    }

    private fun observeTelemetry() = scope.launch {
        telemetryManager.telemetry.collect { tel ->
            val newMode = when {
                tel.uplinkStatus == UplinkStatus.OFFLINE      -> DegradedMode.ISOLATED
                tel.packetLossPct > 40f                       -> DegradedMode.COMMAND_DESYNC_RISK
                tel.uplinkStatus == UplinkStatus.RECONNECTING -> DegradedMode.PARTIAL_TELEMETRY
                tel.packetLossPct > 15f || tel.apiRttMs > 1500L -> DegradedMode.UPLINK_DEGRADED
                tel.uplinkStatus == UplinkStatus.DEGRADED     -> DegradedMode.UPLINK_DEGRADED
                else                                          -> DegradedMode.FULL_OPERATION
            }

            val reason = when (newMode) {
                DegradedMode.ISOLATED             -> "No uplink — all operations local only"
                DegradedMode.COMMAND_DESYNC_RISK  -> "Packet loss ${tel.packetLossPct.toInt()}% — command delivery unconfirmed"
                DegradedMode.PARTIAL_TELEMETRY    -> "Reconnecting (attempt ${tel.reconnectAttempts}) — data may be stale"
                DegradedMode.UPLINK_DEGRADED      -> "RTT ${tel.apiRttMs}ms — reduced operational confidence"
                DegradedMode.FULL_OPERATION       -> ""
            }

            val previous = _state.value.mode
            _state.value = _state.value.copy(mode = newMode, reason = reason)

            // Emit events on mode change
            if (newMode != previous) {
                when (newMode) {
                    DegradedMode.FULL_OPERATION -> eventBus.emit(SystemEvent.UplinkRestored)
                    DegradedMode.ISOLATED       -> eventBus.emit(SystemEvent.UplinkDegraded)
                    else -> Unit
                }
            }
        }
    }

    private fun observeEvents() = scope.launch {
        eventBus.events.collect { event ->
            when (event) {
                is SystemEvent.WebSocketConnected -> {
                    _state.value = _state.value.copy(lastFullSyncMs = System.currentTimeMillis())
                }
                else -> Unit
            }
        }
    }

    fun incrementPending() {
        _state.value = _state.value.copy(pendingCommandsCt = _state.value.pendingCommandsCt + 1)
    }

    fun decrementPending() {
        _state.value = _state.value.copy(
            pendingCommandsCt = (_state.value.pendingCommandsCt - 1).coerceAtLeast(0)
        )
    }
}

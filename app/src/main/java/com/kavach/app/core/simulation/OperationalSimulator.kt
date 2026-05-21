package com.kavach.app.core.simulation

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.ThreatStateManager
import com.kavach.app.core.telemetry.TelemetryManager
import com.kavach.app.core.telemetry.UplinkStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OperationalSimulator — Full tactical environment simulation.
 *
 * Creates a believable, stressful operational environment by scripting
 * realistic battalion traffic, emergencies, and network degradation events.
 *
 * Scenarios (each is an independent Coroutine):
 *
 *   BATTALION_TRAFFIC      — Continuous field activity stream
 *   EMERGENCY_CHAIN        — Escalating threat sequence (WARNING → CRITICAL)
 *   TELEMETRY_STORM        — Packet loss spike + WebSocket churn
 *   MASS_RECONNECT         — Simultaneous reconnect event flood
 *   DEGRADED_NETWORK_TEST  — Extended partial connectivity simulation
 *   COMMAND_OVERRIDE_DRILL — Senanayak issues command override sequence
 *
 * Design rule:
 *   OperationalSimulator should NEVER be active in production builds.
 *   Gate all calls behind BuildConfig.DEBUG or a server-issued simulation flag.
 */

enum class SimulationScenario(val label: String, val durationMs: Long) {
    BATTALION_TRAFFIC      ("Battalion Field Traffic",         120_000L),
    EMERGENCY_CHAIN        ("Emergency Escalation Chain",       45_000L),
    TELEMETRY_STORM        ("Telemetry Degradation Storm",      30_000L),
    MASS_RECONNECT         ("Mass Reconnect Event",             20_000L),
    DEGRADED_NETWORK_TEST  ("Extended Degraded Network",        60_000L),
    COMMAND_OVERRIDE_DRILL ("Command Override Drill",           25_000L),
    NETWORK_CHAOS          ("Real-World Network Chaos",         50_000L),
    FULL_STRESS_TEST       ("Full Operational Stress Test",    350_000L),
}

data class SimulationStatus(
    val running         : Boolean           = false,
    val activeScenario  : SimulationScenario? = null,
    val elapsedMs       : Long              = 0L,
    val eventsFired     : Int               = 0,
    val log             : List<String>      = emptyList()
)

@Singleton
class OperationalSimulator @Inject constructor(
    private val eventBus           : EventBus,
    private val threatStateManager : ThreatStateManager,
    private val telemetryManager   : TelemetryManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(SimulationStatus())
    val status: StateFlow<SimulationStatus> = _status.asStateFlow()

    private var activeJob   : Job? = null
    private var eventCounter: Int  = 0

    // ── Public API ────────────────────────────────────────────

    /** Launch a specific scenario. Cancels any running scenario first. */
    fun run(scenario: SimulationScenario) {
        stop()
        activeJob = scope.launch {
            updateStatus(running = true, scenario = scenario)
            log("▶ Scenario started: ${scenario.label}")
            try {
                when (scenario) {
                    SimulationScenario.BATTALION_TRAFFIC      -> runBattalionTraffic()
                    SimulationScenario.EMERGENCY_CHAIN        -> runEmergencyChain()
                    SimulationScenario.TELEMETRY_STORM        -> runTelemetryStorm()
                    SimulationScenario.MASS_RECONNECT         -> runMassReconnect()
                    SimulationScenario.DEGRADED_NETWORK_TEST  -> runDegradedNetwork()
                    SimulationScenario.COMMAND_OVERRIDE_DRILL -> runCommandOverrideDrill()
                    SimulationScenario.NETWORK_CHAOS          -> runNetworkChaos()
                    SimulationScenario.FULL_STRESS_TEST       -> runFullStressTest()
                }
            } finally {
                log("■ Scenario complete: ${scenario.label} — $eventCounter events fired")
                updateStatus(running = false, scenario = null)
            }
        }
    }

    /** Stop all running simulations and restore normal state. */
    fun stop() {
        activeJob?.cancel()
        activeJob = null
        eventCounter = 0
        // Restore baseline
        threatStateManager.clear()
        telemetryManager.uplinkStatus = UplinkStatus.CONNECTED
        updateStatus(running = false, scenario = null)
        log("■ Simulation stopped — environment restored")
    }

    // ── Scenarios ─────────────────────────────────────────────

    /**
     * BATTALION TRAFFIC — Simulates continuous field activity.
     * Fires a stream of officer activity events at varying intervals.
     */
    private suspend fun runBattalionTraffic() {
        val fieldPnos = listOf("PNO-0042", "PNO-0178", "PNO-0093", "PNO-0215", "PNO-0301")
        val activities = listOf(
            "Patrol sector-7 — no contact",
            "Requesting backup at checkpoint-4",
            "Suspect vehicle observed, licence: MH-12-4421",
            "Field data upload complete",
            "Medical emergency at base-camp-2",
            "Communication blackout zone entered",
            "Civilian interaction logged",
            "Equipment malfunction — radio unit-3"
        )

        repeat(40) {
            if (!isActive) return
            val pno      = fieldPnos.random()
            val activity = activities.random()
            emit(SystemEvent.TrainingCompleted(pno, "field-report-${it}"))
            log("  [TRAFFIC] $pno → $activity")
            delay(Random.nextLong(1_500L, 4_000L))
        }
    }

    /**
     * EMERGENCY CHAIN — Graduated threat escalation sequence.
     * Models a real incident evolving from WARNING → ELEVATED → CRITICAL.
     */
    private suspend fun runEmergencyChain() {
        log("  [CHAIN] Phase 1: Anomaly detected")
        delay(2_000L)

        // Phase 1: Warning
        emit(SystemEvent.ThreatLevelChanged(ThreatLevel.WARNING))
        threatStateManager.setLevel(ThreatLevel.WARNING)
        log("  [CHAIN] Threat escalated → WARNING")
        delay(5_000L)

        // Phase 2: Elevated — issue first advisory
        emit(SystemEvent.ThreatLevelChanged(ThreatLevel.ELEVATED))
        threatStateManager.setLevel(ThreatLevel.ELEVATED)
        log("  [CHAIN] Threat escalated → ELEVATED")
        emit(SystemEvent.EmergencyBroadcast(
            broadcastId = "BCAST-SIM-001",
            message     = "ADVISORY: Elevated security conditions — all units maintain heightened vigilance",
            priority    = "ELEVATED"
        ))
        delay(6_000L)

        // Phase 3: Critical — command override
        emit(SystemEvent.ThreatLevelChanged(ThreatLevel.CRITICAL))
        threatStateManager.setLevel(ThreatLevel.CRITICAL)
        log("  [CHAIN] Threat escalated → CRITICAL")
        emit(SystemEvent.CommandOverride(
            title      = "SECTOR LOCKDOWN ORDER",
            body       = "All units in Sector-7 are to cease movement and maintain radio silence. Incident response team has been dispatched. Await further instruction.",
            issuedBy   = "SENANAYAK COMMAND",
            requiresAck = true
        ))
        delay(8_000L)

        // Phase 4: Mass broadcast + integrity alert
        emit(SystemEvent.EmergencyBroadcast(
            broadcastId = "BCAST-SIM-002",
            message     = "CRITICAL: Unauthorized access attempt on secure infrastructure detected",
            priority    = "CRITICAL"
        ))
        log("  [CHAIN] Emergency broadcast issued")
        delay(5_000L)

        // Phase 5: Begin de-escalation
        log("  [CHAIN] Phase 5: Incident contained — de-escalating")
        emit(SystemEvent.ThreatCleared)
        threatStateManager.setLevel(ThreatLevel.WARNING)
        delay(3_000L)
        threatStateManager.setLevel(ThreatLevel.SECURE)
        log("  [CHAIN] Environment restored to SECURE")
    }

    /**
     * TELEMETRY STORM — Packet loss spike + WebSocket instability.
     * Tests system behavior under severe connectivity degradation.
     */
    private suspend fun runTelemetryStorm() {
        log("  [STORM] Inducing telemetry degradation")

        // Phase 1: Latency spike
        repeat(5) { i ->
            if (!isActive) return
            telemetryManager.recordApiRtt(800L + (i * 400L))
            telemetryManager.recordWsLatency(200L + (i * 100L))
            log("  [STORM] RTT: ${800 + i * 400}ms, WS: ${200 + i * 100}ms")
            delay(1_500L)
        }

        // Phase 2: WebSocket drops
        repeat(3) { attempt ->
            if (!isActive) return
            telemetryManager.onWsReconnecting(attempt + 1)
            emit(SystemEvent.WebSocketReconnecting(attempt + 1))
            log("  [STORM] WS reconnect attempt #${attempt + 1}")
            delay(Random.nextLong(2_000L, 4_000L))
            telemetryManager.onWsConnected()
            emit(SystemEvent.WebSocketConnected)
            log("  [STORM] WS restored")
            delay(1_000L)
        }

        // Phase 3: Full uplink loss
        log("  [STORM] Uplink offline")
        telemetryManager.onUplinkOffline()
        emit(SystemEvent.UplinkDegraded)
        delay(6_000L)

        // Phase 4: Recovery
        telemetryManager.onUplinkRestored()
        emit(SystemEvent.UplinkRestored)
        log("  [STORM] Uplink restored — normalizing")
        repeat(5) { i ->
            telemetryManager.recordApiRtt(400L - (i * 70L))
            delay(1_000L)
        }
    }

    /**
     * MASS RECONNECT — Simulates all devices reconnecting simultaneously.
     * Tests the jitter + backoff system under reconnect storm conditions.
     */
    private suspend fun runMassReconnect() {
        log("  [RECONNECT] Simulating mass reconnect event (battalion-scale)")
        val deviceCount = 12
        repeat(deviceCount) { deviceIndex ->
            if (!isActive) return
            val delayMs = Random.nextLong(0L, 500L) // Jitter: devices reconnect within 500ms window
            delay(delayMs)
            emit(SystemEvent.WebSocketReconnecting(1))
            log("  [RECONNECT] Device-$deviceIndex reconnecting...")
        }
        delay(3_000L)
        // All reconnect
        repeat(deviceCount) { deviceIndex ->
            if (!isActive) return
            emit(SystemEvent.WebSocketConnected)
            log("  [RECONNECT] Device-$deviceIndex connected")
            delay(Random.nextLong(100L, 800L)) // Staggered reconnect
        }
        emit(SystemEvent.UplinkRestored)
        log("  [RECONNECT] All $deviceCount devices restored")
    }

    /**
     * DEGRADED NETWORK — Extended partial connectivity.
     * Simulates field operations in low-bandwidth environments.
     */
    private suspend fun runDegradedNetwork() {
        log("  [DEGRADED] Entering extended degraded network simulation")
        telemetryManager.uplinkStatus = UplinkStatus.DEGRADED
        telemetryManager.recordApiRtt(1_200L)

        repeat(20) { tick ->
            if (!isActive) return
            // RTT fluctuates between 800ms and 2500ms
            val rtt = 800L + Random.nextLong(0L, 1700L)
            telemetryManager.recordApiRtt(rtt)
            // Occasional brief reconnects
            if (tick % 5 == 0) {
                telemetryManager.onWsReconnecting(1)
                emit(SystemEvent.WebSocketReconnecting(1))
                delay(1_500L)
                telemetryManager.onWsConnected()
                emit(SystemEvent.WebSocketConnected)
                log("  [DEGRADED] Tick $tick: RTT=${rtt}ms, brief reconnect")
            }
            delay(2_500L)
        }
        telemetryManager.uplinkStatus = UplinkStatus.CONNECTED
        telemetryManager.recordApiRtt(45L)
        log("  [DEGRADED] Network normalized")
    }

    /**
     * COMMAND OVERRIDE DRILL — Full command interruption sequence.
     * Tests the overlay priority matrix and mandatory acknowledgment flow.
     */
    private suspend fun runCommandOverrideDrill() {
        log("  [DRILL] Initiating command override drill")
        delay(2_000L)

        emit(SystemEvent.AuditCaptureActive)
        log("  [DRILL] Audit capture active")
        delay(1_000L)

        // Issue advisory
        emit(SystemEvent.CommandOverride(
            title      = "DRILL — READINESS CHECK",
            body       = "This is a command readiness drill. All personnel must acknowledge receipt within 2 minutes. Failure to acknowledge will be logged.",
            issuedBy   = "CO COMMAND [DRILL]",
            requiresAck = true
        ))
        log("  [DRILL] Command override issued — awaiting acknowledgment")
        delay(5_000L)

        // Escalate to lockdown
        log("  [DRILL] Escalating to lockdown drill phase")
        emit(SystemEvent.LockdownActivated("SENANAYAK [DRILL]"))
        delay(8_000L)

        // Release lockdown
        emit(SystemEvent.LockdownLifted)
        log("  [DRILL] Lockdown drill phase complete — released")
        delay(2_000L)

        emit(SystemEvent.ThreatCleared)
        threatStateManager.clear()
        log("  [DRILL] Command override drill complete")
    }

    /**
     * FULL STRESS TEST — All scenarios run sequentially.
     * Maximum operational pressure for stress validation.
     */
    private suspend fun runFullStressTest() {
        log("  [STRESS] Full operational stress test — initiating all scenarios")
        runBattalionTraffic()
        delay(3_000L)
        runTelemetryStorm()
        delay(3_000L)
        runEmergencyChain()
        delay(3_000L)
        runMassReconnect()
        delay(3_000L)
        runNetworkChaos()
        delay(3_000L)
        runCommandOverrideDrill()
        log("  [STRESS] Full stress test complete")
    }

    /**
     * NETWORK CHAOS — Real-world field network failure modes.
     *
     * Unlike TELEMETRY_STORM (which tests latency/reconnect),
     * this models the specific failure patterns seen in field deployments:
     *   1. Carrier switch (RTT spike + brief WS drop)
     *   2. Dead radio zone (total uplink blackout, slow recovery)
     *   3. Captive portal (DNS resolves but traffic blocked)
     *   4. Half-open WebSocket (connected but server-side silent)
     *   5. Packet corruption burst (high loss, rapidly recovering)
     */
    private suspend fun runNetworkChaos() {
        log("  [CHAOS] Real-world network chaos sequence starting")

        // Phase 1: Carrier switching (4G → WiFi)
        // RTT jumps to 800ms then stabilizes at 120ms
        log("  [CHAOS] Phase 1: Carrier switch simulation")
        telemetryManager.recordApiRtt(800L)
        telemetryManager.onWsReconnecting(1)
        emit(SystemEvent.WebSocketReconnecting(1))
        delay(1_500L)
        telemetryManager.recordApiRtt(120L)
        telemetryManager.onWsConnected()
        emit(SystemEvent.WebSocketConnected)
        log("  [CHAOS] Carrier switch complete — RTT normalized to 120ms")
        delay(3_000L)

        // Phase 2: Radio dead zone (total blackout)
        log("  [CHAOS] Phase 2: Radio dead zone entered")
        telemetryManager.onUplinkOffline()
        emit(SystemEvent.UplinkDegraded)
        // Simulate extended blackout — 12 seconds with no recovery
        repeat(4) { tick ->
            if (!isActive) return
            log("  [CHAOS] Dead zone — tick ${tick + 1}/4 (no signal)")
            delay(3_000L)
        }
        // Exit dead zone — slow re-acquisition
        log("  [CHAOS] Dead zone exited — re-acquiring signal")
        telemetryManager.recordApiRtt(2_400L)  // Initial re-acquisition RTT
        telemetryManager.onWsReconnecting(1)
        emit(SystemEvent.WebSocketReconnecting(1))
        delay(2_000L)
        telemetryManager.onWsConnected()
        telemetryManager.onUplinkRestored()
        emit(SystemEvent.WebSocketConnected)
        emit(SystemEvent.UplinkRestored)
        log("  [CHAOS] Signal re-acquired — RTT stabilizing")
        repeat(4) { i ->
            telemetryManager.recordApiRtt(2_000L - (i * 400L))
            delay(800L)
        }
        delay(2_000L)

        // Phase 3: Captive portal (DNS resolves, traffic blocked)
        // Modeled as: WS appears connected but RTT spikes to max, packets lost
        log("  [CHAOS] Phase 3: Captive portal interference")
        repeat(6) { tick ->
            if (!isActive) return
            // Simulate blocked traffic: very high RTT, no actual data
            telemetryManager.recordApiRtt(Random.nextLong(3_000L, 5_000L))
            telemetryManager.recordWsLatency(Random.nextLong(2_000L, 4_000L))
            if (tick == 3) {
                // Portal eventually redirects — WS drops
                telemetryManager.onWsReconnecting(1)
                emit(SystemEvent.WebSocketReconnecting(1))
                log("  [CHAOS] Captive portal redirect — WS dropped")
            }
            delay(1_500L)
        }
        // Portal cleared
        telemetryManager.onWsConnected()
        emit(SystemEvent.WebSocketConnected)
        telemetryManager.recordApiRtt(180L)
        log("  [CHAOS] Captive portal resolved — traffic restored")
        delay(2_000L)

        // Phase 4: Half-open WebSocket (TCP connected, server silent)
        // WS appears connected but no messages arrive for 10s
        // Client must detect via ping timeout
        log("  [CHAOS] Phase 4: Half-open WebSocket (server silent for 10s)")
        // WS latency grows unboundedly as ping responses stop arriving
        repeat(5) { i ->
            if (!isActive) return
            telemetryManager.recordWsLatency(500L + (i * 800L))
            log("  [CHAOS] Half-open: WS latency ${500 + i * 800}ms (no pong)")
            delay(2_000L)
        }
        // Timeout detected — force reconnect
        telemetryManager.onWsReconnecting(1)
        emit(SystemEvent.WebSocketReconnecting(1))
        delay(1_500L)
        telemetryManager.onWsConnected()
        emit(SystemEvent.WebSocketConnected)
        telemetryManager.recordWsLatency(35L)
        log("  [CHAOS] Half-open resolved — WS re-established")
        delay(2_000L)

        // Phase 5: Packet corruption burst
        // High packet loss (simulated via RTT spikes) that recovers quickly
        log("  [CHAOS] Phase 5: Packet corruption burst")
        repeat(8) { tick ->
            if (!isActive) return
            // Simulate packets arriving corrupted (modeled as timeout + retry = high RTT)
            val corruptionRtt = if (tick < 5) Random.nextLong(1_500L, 3_000L) else
                                              Random.nextLong(100L, 300L)  // Recovery
            telemetryManager.recordApiRtt(corruptionRtt)
            log("  [CHAOS] Corruption tick $tick: RTT=${corruptionRtt}ms")
            delay(1_000L)
        }

        log("  [CHAOS] Network chaos sequence complete — all phases executed")
        // Restore baseline
        telemetryManager.uplinkStatus = UplinkStatus.CONNECTED
        telemetryManager.recordApiRtt(45L)
        telemetryManager.recordWsLatency(20L)
    }

    // ── Utilities ─────────────────────────────────────────────

    private fun emit(event: SystemEvent) {
        eventBus.emit(event)
        eventCounter++
    }

    private fun log(message: String) {
        val current = _status.value.log.toMutableList()
        if (current.size > 100) current.removeFirst()
        current.add(message)
        _status.value = _status.value.copy(log = current, eventsFired = eventCounter)
    }

    private fun updateStatus(running: Boolean, scenario: SimulationScenario?) {
        _status.value = _status.value.copy(running = running, activeScenario = scenario)
    }

    private val isActive get() = activeJob?.isActive == true
}

package com.kavach.app.core.metrics

import com.kavach.app.core.degraded.DegradedModeController
import com.kavach.app.core.degraded.DegradedMode
import com.kavach.app.core.latency.CommandLatencyTracker
import com.kavach.app.core.latency.CommandDeliveryState
import com.kavach.app.core.reconciliation.StateReconciliationEngine
import com.kavach.app.core.reconciliation.ReconciliationResult
import com.kavach.app.core.security.ThreatStateManager
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.perf.PerformanceMonitor
import com.kavach.app.core.perf.HealthGrade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TrustMetrics — Operational trust dashboard data engine.
 *
 * Aggregates signals from 5 subsystems into a single observable
 * [TrustSnapshot] that the admin/pilot dashboard surfaces as:
 * "How much can the operator trust what they are seeing right now?"
 *
 * This is strategic insight, not raw telemetry:
 *   - Reconciliation confidence: has state been verified with server?
 *   - Propagation confidence:    are commands reaching their targets?
 *   - Integrity score:           is the environment uncompromised?
 *   - System health:             is the runtime performing within limits?
 *   - Uplink confidence:         is connectivity reliable?
 *
 * Overall TrustScore (0-100) is the weighted composite.
 * Displayed in admin diagnostics and pilot command center header.
 *
 * Design rule: purely reactive — no new EventBus subscriptions,
 * only combines existing StateFlows from injected systems.
 */

data class TrustSnapshot(
    val reconciliationScore : Int,        // 0-100: state verified with server
    val propagationScore    : Int,        // 0-100: command delivery confidence
    val integrityScore      : Int,        // 0-100: environment trust
    val systemHealthScore   : Int,        // 0-100: runtime performance
    val uplinkScore         : Int,        // 0-100: connectivity confidence
    val overallScore        : Int,        // Weighted composite
    val overallLabel        : TrustLevel,
    val degradedIndicators  : List<String> // Human-readable reasons for reduced trust
)

enum class TrustLevel(val label: String, val colorHex: Long) {
    TRUSTED   ("FULLY TRUSTED",       0xFF16A34A),
    HIGH      ("HIGH CONFIDENCE",     0xFF4ADE80),
    MODERATE  ("MODERATE CONFIDENCE", 0xFFF59E0B),
    LOW       ("LOW CONFIDENCE",      0xFFEA580C),
    UNTRUSTED ("OPERATIONAL RISK",    0xFFDC2626)
}

@Singleton
class TrustMetrics @Inject constructor(
    private val degradedModeController : DegradedModeController,
    private val commandLatencyTracker  : CommandLatencyTracker,
    private val reconciliationEngine   : StateReconciliationEngine,
    private val threatStateManager     : ThreatStateManager,
    private val performanceMonitor     : PerformanceMonitor
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(
        TrustSnapshot(
            reconciliationScore = 100,
            propagationScore    = 100,
            integrityScore      = 100,
            systemHealthScore   = 100,
            uplinkScore         = 100,
            overallScore        = 100,
            overallLabel        = TrustLevel.TRUSTED,
            degradedIndicators  = emptyList()
        )
    )
    val snapshot: StateFlow<TrustSnapshot> = _snapshot.asStateFlow()

    init { startAggregation() }

    private fun startAggregation() = scope.launch {
        combine(
            degradedModeController.state,
            commandLatencyTracker.traces,
            reconciliationEngine.result,
            threatStateManager.currentLevel,
            performanceMonitor.snapshot
        ) { degradedState, traces, reconciliation, threat, perf ->

            val indicators = mutableListOf<String>()

            // -- Reconciliation Score -----------------------------------------
            val reconciliationScore = when (reconciliation) {
                is ReconciliationResult.InSync     -> 100
                is ReconciliationResult.Reconciled -> {
                    indicators.add("State reconciled (${reconciliation.divergedFields.size} field(s) corrected)")
                    80
                }
                is ReconciliationResult.Failed -> {
                    indicators.add("Reconciliation failed: ${reconciliation.reason}")
                    30
                }
                is ReconciliationResult.InProgress -> 70
            }

            // -- Propagation Score -------------------------------------------
            val recentTraces = traces.take(10)
            val propagationScore = if (recentTraces.isEmpty()) {
                100
            } else {
                val avg = recentTraces.map { it.confidencePct }.average().toInt()
                val failCount = recentTraces.count { it.state == CommandDeliveryState.FAILED }
                if (failCount > 0) indicators.add("$failCount command(s) failed delivery")
                avg
            }

            // -- Integrity Score (threat level) --------------------------------
            val integrityScore = when (threat) {
                ThreatLevel.SECURE      -> 100
                ThreatLevel.WARNING     -> { indicators.add("Threat level: WARNING"); 85 }
                ThreatLevel.ELEVATED    -> { indicators.add("Threat level: ELEVATED"); 65 }
                ThreatLevel.CRITICAL    -> { indicators.add("Threat level: CRITICAL"); 40 }
                ThreatLevel.COMPROMISED -> { indicators.add("Environment COMPROMISED"); 0  }
            }

            // -- System Health Score ------------------------------------------
            val systemHealthScore = when (perf.healthGrade) {
                HealthGrade.NOMINAL  -> 100
                HealthGrade.DEGRADED -> {
                    indicators.add("Performance degraded (${perf.memoryPressurePct.toInt()}% memory)")
                    75
                }
                HealthGrade.STRESSED -> {
                    indicators.add("System stressed — ${perf.droppedFrameCount} dropped frames")
                    50
                }
                HealthGrade.CRITICAL -> {
                    indicators.add("CRITICAL performance — operator UI may be affected")
                    20
                }
            }

            // -- Uplink Confidence from DegradedMode --------------------------
            val uplinkScore = degradedState.mode.confidencePct
            if (degradedState.mode != DegradedMode.FULL_OPERATION) {
                indicators.add("Uplink: ${degradedState.mode.label} (${uplinkScore}% confidence)")
            }

            // -- Weighted Composite -------------------------------------------
            // Weights reflect operational priority
            val overall = (
                reconciliationScore * 0.20 +  // 20% — server state verified
                propagationScore    * 0.25 +  // 25% — commands reaching targets
                integrityScore      * 0.25 +  // 25% — environment trust
                systemHealthScore   * 0.15 +  // 15% — runtime health
                uplinkScore         * 0.15    // 15% — connectivity
            ).toInt().coerceIn(0, 100)

            val level = when {
                overall >= 95 -> TrustLevel.TRUSTED
                overall >= 80 -> TrustLevel.HIGH
                overall >= 60 -> TrustLevel.MODERATE
                overall >= 35 -> TrustLevel.LOW
                else          -> TrustLevel.UNTRUSTED
            }

            TrustSnapshot(
                reconciliationScore = reconciliationScore,
                propagationScore    = propagationScore,
                integrityScore      = integrityScore,
                systemHealthScore   = systemHealthScore,
                uplinkScore         = uplinkScore,
                overallScore        = overall,
                overallLabel        = level,
                degradedIndicators  = indicators
            )
        }.collect { _snapshot.value = it }
    }
}

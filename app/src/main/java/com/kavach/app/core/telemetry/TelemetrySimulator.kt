package com.kavach.app.core.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * TelemetrySimulator — Live data simulation layer.
 *
 * Produces realistic fluctuating telemetry for demo/pilot environments
 * where real backend measurements may not be available.
 *
 * Simulates:
 *  - Heartbeat fluctuation (BPM variance)
 *  - Packet loss spikes (occasional bursts)
 *  - API RTT variation (jitter model)
 *  - Activity pulse (periodic events)
 *  - Threat spikes (rare, auto-resolve)
 *  - Conference bitrate drops (bandwidth simulation)
 *
 * Usage: call [start] once; inject into ViewModel or Application.
 */
class TelemetrySimulator(
    private val telemetryManager: TelemetryManager
) {

    companion object {
        private const val TICK_MS            = 2_500L
        private const val SPIKE_PROBABILITY  = 0.08f   // 8% chance per tick
        private const val SPIKE_DURATION_MS  = 6_000L
    }

    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Activity pulse counter for "live feel"
    private val _activityPulse  = MutableStateFlow(0)
    val activityPulse: StateFlow<Int> = _activityPulse.asStateFlow()

    // Simulated heartbeat BPM
    private val _heartbeatBpm   = MutableStateFlow(72)
    val heartbeatBpm: StateFlow<Int> = _heartbeatBpm.asStateFlow()

    private var job: Job? = null
    private var spikeJob: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        spikeJob?.cancel()
    }

    private fun tick() {
        // Heartbeat jitter: ±5 BPM around 72
        val bpm = 72 + Random.nextInt(-5, 6)
        _heartbeatBpm.value = bpm.coerceIn(55, 90)

        // API RTT: base 45ms with ±20ms jitter, occasional 150ms spike
        val rtt = 45L + Random.nextLong(-20L, 20L) +
                  if (Random.nextFloat() < 0.1f) Random.nextLong(100L, 300L) else 0L
        telemetryManager.recordApiRtt(rtt.coerceAtLeast(10L))

        // WS latency: base 18ms ±10ms
        val wsLat = 18L + Random.nextLong(-8L, 10L)
        telemetryManager.recordWsLatency(wsLat.coerceAtLeast(5L))

        // Activity pulse increment
        _activityPulse.value = (_activityPulse.value + 1) % Int.MAX_VALUE

        // Rare packet loss spike
        if (Random.nextFloat() < SPIKE_PROBABILITY) {
            triggerSpikeEvent()
        }
    }

    /** Simulate a transient degradation event (packet loss spike). */
    private fun triggerSpikeEvent() {
        if (spikeJob?.isActive == true) return
        spikeJob = scope.launch {
            // Spike: degrade uplink briefly
            telemetryManager.onWsReconnecting(1)
            delay(SPIKE_DURATION_MS)
            // Recover
            telemetryManager.onWsConnected()
        }
    }
}

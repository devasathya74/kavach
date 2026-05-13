package com.kavach.app.core.perf

import android.os.SystemClock
import android.view.Choreographer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PerformanceMonitor — Runtime health instrumentation.
 *
 * Tracks the operational cost of the infrastructure layer without
 * adding meaningful overhead itself. Used to detect:
 *
 *   - EventBus throughput spikes (events/sec)
 *   - Frame drops (Choreographer callback delta > 20ms)
 *   - Memory pressure progression
 *   - StateFlow emission rate (proxy for recomposition pressure)
 *   - Overlay active duration (cognitive load correlation)
 *
 * All metrics are observable as [PerformanceSnapshot] StateFlow.
 * In production: surface only on DEBUG builds or admin diagnostics screen.
 *
 * Design rule:
 *   This monitor must NOT use SharedFlow or EventBus internally —
 *   it must be completely independent of the systems it monitors.
 *   Otherwise it distorts its own measurements.
 */

data class PerformanceSnapshot(
    val capturedAtMs          : Long    = 0L,
    val eventBusEpsLast5s     : Int     = 0,   // Events per second (5s window)
    val totalEventsDispatched : Long    = 0L,
    val droppedFrameCount     : Int     = 0,
    val lastFrameDeltaMs      : Long    = 0L,
    val memoryUsedMb          : Float   = 0f,
    val memoryMaxMb           : Float   = 0f,
    val memoryPressurePct     : Float   = 0f,
    val stateFlowEmissions5s  : Int     = 0,   // Proxy for recomposition pressure
    val overlayActiveMs       : Long    = 0L,
    val activeCoroutines      : Int     = 0,
    val healthGrade           : HealthGrade = HealthGrade.NOMINAL
)

enum class HealthGrade(val label: String) {
    NOMINAL  ("NOMINAL"),
    DEGRADED ("DEGRADED"),
    STRESSED ("STRESSED"),
    CRITICAL ("CRITICAL PERFORMANCE")
}

@Singleton
class PerformanceMonitor @Inject constructor() {

    companion object {
        private const val SAMPLE_WINDOW_MS = 5_000L
        private const val FRAME_DROP_THRESHOLD_MS = 20L  // > 20ms delta = dropped frame
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(PerformanceSnapshot())
    val snapshot: StateFlow<PerformanceSnapshot> = _snapshot.asStateFlow()

    // Counters — all thread-safe atomics
    private val totalEvents     = AtomicLong(0L)
    private val windowEvents    = AtomicInteger(0)
    private val windowEmissions = AtomicInteger(0)
    private val droppedFrames   = AtomicInteger(0)

    // Overlay timing
    private var overlayStartMs  = 0L
    private var overlayTotalMs  = 0L

    // Choreographer frame tracking
    private var lastFrameMs     = 0L
    private var lastDeltaMs     = 0L

    private var choreographerCallback: Choreographer.FrameCallback? = null
    private var samplingJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────

    fun start() {
        startFrameTracking()
        startSamplingLoop()
    }

    fun stop() {
        choreographerCallback = null
        samplingJob?.cancel()
    }

    // ── Event tracking (call from EventBus.emit) ──────────────

    /** Call from EventBus immediately after each emit. Zero-cost in production. */
    fun recordEvent() {
        totalEvents.incrementAndGet()
        windowEvents.incrementAndGet()
    }

    /** Call from any StateFlow update to track emission pressure. */
    fun recordStateFlowEmission() {
        windowEmissions.incrementAndGet()
    }

    // ── Overlay tracking ──────────────────────────────────────

    fun onOverlayShown() {
        overlayStartMs = SystemClock.elapsedRealtime()
    }

    fun onOverlayDismissed() {
        if (overlayStartMs > 0) {
            overlayTotalMs += SystemClock.elapsedRealtime() - overlayStartMs
            overlayStartMs = 0
        }
    }

    // ── Internal ──────────────────────────────────────────────

    private fun startFrameTracking() {
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val nowMs = frameTimeNanos / 1_000_000L
                if (lastFrameMs > 0) {
                    val deltaMs = nowMs - lastFrameMs
                    lastDeltaMs = deltaMs
                    if (deltaMs > FRAME_DROP_THRESHOLD_MS) {
                        droppedFrames.incrementAndGet()
                    }
                }
                lastFrameMs = nowMs
                choreographerCallback?.let {
                    Choreographer.getInstance().postFrameCallback(it)
                }
            }
        }
        choreographerCallback = callback
        // Must be posted from main thread
        scope.launch(Dispatchers.Main) {
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    private fun startSamplingLoop() {
        samplingJob = scope.launch {
            while (isActive) {
                delay(SAMPLE_WINDOW_MS)
                sample()
            }
        }
    }

    private fun sample() {
        val rt       = Runtime.getRuntime()
        val usedMb   = (rt.totalMemory() - rt.freeMemory()) / 1_048_576f
        val maxMb    = rt.maxMemory() / 1_048_576f
        val pressurePct = (usedMb / maxMb) * 100f

        val eps      = windowEvents.getAndSet(0)
        val emissions= windowEmissions.getAndSet(0)

        val grade = when {
            pressurePct > 85f || eps > 100 || droppedFrames.get() > 10 -> HealthGrade.CRITICAL
            pressurePct > 70f || eps > 50  || droppedFrames.get() > 5  -> HealthGrade.STRESSED
            pressurePct > 55f || eps > 20                               -> HealthGrade.DEGRADED
            else                                                         -> HealthGrade.NOMINAL
        }

        _snapshot.value = PerformanceSnapshot(
            capturedAtMs          = System.currentTimeMillis(),
            eventBusEpsLast5s     = eps / 5,
            totalEventsDispatched = totalEvents.get(),
            droppedFrameCount     = droppedFrames.get(),
            lastFrameDeltaMs      = lastDeltaMs,
            memoryUsedMb          = usedMb,
            memoryMaxMb           = maxMb,
            memoryPressurePct     = pressurePct,
            stateFlowEmissions5s  = emissions / 5,
            overlayActiveMs       = overlayTotalMs,
            healthGrade           = grade
        )
    }
}

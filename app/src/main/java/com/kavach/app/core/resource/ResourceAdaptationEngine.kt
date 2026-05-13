package com.kavach.app.core.resource

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResourceAdaptationEngine — Device-aware UI degradation policy.
 *
 * Detects device tier via RAM + CPU + API level.
 * Emits AdaptationPolicy for UI components to scale behavior.
 * Degrades one tier under onTrimMemory pressure.
 */

enum class DeviceTier { LOW_END, MID_RANGE, HIGH_END }

data class AdaptationPolicy(
    val tier                        : DeviceTier,
    val animationsEnabled           : Boolean,
    val animationDurationMultiplier : Float,
    val telemetryIntervalMs         : Long,
    val overlayComplexityFull       : Boolean,
    val pulseEffectsEnabled         : Boolean,
    val graphUpdateIntervalMs       : Long,
    val maxConcurrentAnimations     : Int
) {
    companion object {
        val LOW_END = AdaptationPolicy(
            tier                        = DeviceTier.LOW_END,
            animationsEnabled           = false,
            animationDurationMultiplier = 0f,
            telemetryIntervalMs         = 10_000L,
            overlayComplexityFull       = false,
            pulseEffectsEnabled         = false,
            graphUpdateIntervalMs       = 15_000L,
            maxConcurrentAnimations     = 1
        )
        val MID_RANGE = AdaptationPolicy(
            tier                        = DeviceTier.MID_RANGE,
            animationsEnabled           = true,
            animationDurationMultiplier = 0.6f,
            telemetryIntervalMs         = 5_000L,
            overlayComplexityFull       = false,
            pulseEffectsEnabled         = true,
            graphUpdateIntervalMs       = 8_000L,
            maxConcurrentAnimations     = 3
        )
        val HIGH_END = AdaptationPolicy(
            tier                        = DeviceTier.HIGH_END,
            animationsEnabled           = true,
            animationDurationMultiplier = 1.0f,
            telemetryIntervalMs         = 3_000L,
            overlayComplexityFull       = true,
            pulseEffectsEnabled         = true,
            graphUpdateIntervalMs       = 3_000L,
            maxConcurrentAnimations     = Int.MAX_VALUE
        )
    }
}

@Singleton
class ResourceAdaptationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _policy = MutableStateFlow(detect())
    val policy: StateFlow<AdaptationPolicy> = _policy.asStateFlow()

    val currentTier get() = _policy.value.tier

    /** Call from Application.onTrimMemory(). */
    fun onMemoryPressure(level: Int) {
        if (level >= 40) {
            _policy.value = when (_policy.value.tier) {
                DeviceTier.HIGH_END  -> AdaptationPolicy.MID_RANGE
                DeviceTier.MID_RANGE -> AdaptationPolicy.LOW_END
                DeviceTier.LOW_END   -> AdaptationPolicy.LOW_END
            }
        }
    }

    fun onMemoryRestored() { _policy.value = detect() }

    private fun detect(): AdaptationPolicy {
        val am       = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem      = am.memoryClass
        val cores    = Runtime.getRuntime().availableProcessors()
        val api      = Build.VERSION.SDK_INT
        return when {
            mem >= 256 && cores >= 6 && api >= 28 -> AdaptationPolicy.HIGH_END
            mem < 128 || cores <= 2 || api < 23   -> AdaptationPolicy.LOW_END
            else                                   -> AdaptationPolicy.MID_RANGE
        }
    }
}

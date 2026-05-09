package com.kavach.app.data.sync

enum class SyncProfile {
    AGGRESSIVE, // Realtime, full assets
    NORMAL,     // Standard heartbeat/sync
    DEGRADED,   // Coalesced, no thumbnails
    CRITICAL    // Emergency-only, no media, minimal heartbeat
}

object AdaptiveThrottling {
    /**
     * Fleet Survivability Layer.
     * Calculates sync profile based on device telemetry (Battery, Thermal, Signal).
     */
    
    fun calculateProfile(
        batteryPercent: Int,
        isCharging: Boolean,
        thermalStatus: Int, // 0-6 (Normal to Shutdown)
        networkQuality: Int // 0-4
    ): SyncProfile {
        if (batteryPercent < 10 && !isCharging) return SyncProfile.CRITICAL
        if (thermalStatus >= 3) return SyncProfile.CRITICAL // High thermal pressure
        if (batteryPercent < 25) return SyncProfile.DEGRADED
        if (networkQuality <= 1) return SyncProfile.DEGRADED
        
        return if (isCharging) SyncProfile.AGGRESSIVE else SyncProfile.NORMAL
    }
}

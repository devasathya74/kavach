package com.kavach.app.domain.model

import java.util.Date

/**
 * DeviceStatus — The core state machine for device health and connectivity.
 */
sealed interface DeviceStatus {
    object Online : DeviceStatus
    object Offline : DeviceStatus
    object Degraded : DeviceStatus // Connected but high latency or failed integrity
    object Stale : DeviceStatus    // No heartbeat for a defined period
    object Unregistered : DeviceStatus
    object Blocked : DeviceStatus
}

/**
 * DeviceHealth — A domain model for device health status.
 */
data class DeviceHealth(
    val status: DeviceStatus,
    val lastHeartbeatAt: Date?,
    val trustScore: Float,
    val integrityLevel: String
)

fun calculateDeviceStatus(lastHeartbeat: Long?, isBlocked: Boolean): DeviceStatus {
    if (isBlocked) return DeviceStatus.Blocked
    if (lastHeartbeat == null) return DeviceStatus.Unregistered
    
    val diff = System.currentTimeMillis() - lastHeartbeat
    return when {
        diff < 60_000 -> DeviceStatus.Online      // < 1 min
        diff < 300_000 -> DeviceStatus.Degraded   // < 5 min
        diff < 3600_000 -> DeviceStatus.Stale     // < 1 hour
        else -> DeviceStatus.Offline
    }
}

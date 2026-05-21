package com.kavach.app.ui.screens.pilot.devices

import com.kavach.app.data.local.entity.OfficerDeviceCacheEntity
import com.kavach.app.domain.model.DeviceStatus
import com.kavach.app.domain.model.calculateDeviceStatus

data class DeviceListItemUiModel(
    val id: String,
    val officerId: String,
    val deviceId: String,
    val name: String,
    val status: DeviceStatus,
    val trustScore: Float,
    val integrityLevel: String,
    val lastHeartbeatAt: Long?,
    val displayStatus: String
)

fun OfficerDeviceCacheEntity.toUiModel(): DeviceListItemUiModel {
    val deviceStatus = calculateDeviceStatus(
        lastHeartbeat = lastHeartbeatAt,
        isBlocked = status == "BLOCKED"
    )
    
    return DeviceListItemUiModel(
        id = id,
        officerId = officerId,
        deviceId = deviceId,
        name = deviceName,
        status = deviceStatus,
        trustScore = trustScore,
        integrityLevel = integrityLevel,
        lastHeartbeatAt = lastHeartbeatAt,
        displayStatus = when (deviceStatus) {
            DeviceStatus.Online -> "Online"
            DeviceStatus.Offline -> "Offline"
            DeviceStatus.Degraded -> "Degraded"
            DeviceStatus.Stale -> "Stale"
            DeviceStatus.Unregistered -> "New"
            DeviceStatus.Blocked -> "Blocked"
        }
    )
}

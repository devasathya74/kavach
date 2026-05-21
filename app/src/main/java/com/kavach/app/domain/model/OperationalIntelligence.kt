package com.kavach.app.domain.model

data class OperationalIntelligence(
    val activeIncidentsCount: Int = 0,
    val highSeverityIncidentsCount: Int = 0,
    
    val unreadBroadcastsCount: Int = 0,
    val unacknowledgedOrdersCount: Int = 0,
    
    val totalPersonnelCount: Int = 0,
    val offlineOfficersCount: Int = 0,
    val activeDevicesCount: Int = 0,
    val staleDevicesCount: Int = 0,
    
    val pendingApprovalsCount: Int = 0,
    
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val systemHealth: SystemHealth = SystemHealth.STABLE
)

enum class SystemHealth {
    STABLE, DEGRADED, CRITICAL
}

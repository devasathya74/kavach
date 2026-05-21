package com.kavach.app.core.intelligence

import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.dao.IncidentDao
import com.kavach.app.data.local.dao.OfficerDao
import com.kavach.app.data.local.dao.OrderDao
import com.kavach.app.domain.model.OperationalIntelligence
import com.kavach.app.domain.model.SystemHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PilotOpsAggregator — The "Operational Brain" for the Pilot Runtime.
 * Combines real-time flows from all operational repositories into a 
 * single, high-level OperationalIntelligence state.
 */
@Singleton
class PilotOpsAggregator @Inject constructor(
    private val incidentDao: IncidentDao,
    private val orderDao: OrderDao,
    private val broadcastDao: BroadcastDao,
    private val officerDao: OfficerDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val flowsGroup1 = combine(
        incidentDao.observeAllIncidents(),
        orderDao.observeAllOrders(),
        broadcastDao.observeBroadcastsWithStats()
    ) { incidents, orders, broadcasts ->
        Triple(incidents, orders, broadcasts)
    }

    private val flowsGroup2 = combine(
        officerDao.getOfficerCount(),
        officerDao.getDeviceCount(),
        officerDao.getActiveDeviceCount()
    ) { officerCount, deviceCount, activeDeviceCount ->
        Triple(officerCount, deviceCount, activeDeviceCount)
    }

    val intelligence: StateFlow<OperationalIntelligence> = combine(
        flowsGroup1,
        flowsGroup2
    ) { group1, group2 ->
        val (incidents, orders, broadcasts) = group1
        val (officerCount, deviceCount, activeDeviceCount) = group2
        
        val activeIncidents = incidents.filter { it.syncStatus == "ACTIVE" || it.syncStatus == "PENDING" }
        val highSeverityIncidents = activeIncidents.filter { it.severity == "HIGH" || it.severity == "CRITICAL" }
        
        val unackedOrders = orders.filter { it.status == "PENDING" }
        val unreadBroadcasts = broadcasts.filter { it.readCount == 0 } // Aggregated stat from DAO

        OperationalIntelligence(
            activeIncidentsCount = activeIncidents.size,
            highSeverityIncidentsCount = highSeverityIncidents.size,
            unacknowledgedOrdersCount = unackedOrders.size,
            unreadBroadcastsCount = unreadBroadcasts.size,
            totalPersonnelCount = officerCount,
            activeDevicesCount = activeDeviceCount,
            staleDevicesCount = deviceCount - activeDeviceCount,
            systemHealth = determineHealth(highSeverityIncidents.size, activeDeviceCount, deviceCount)
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OperationalIntelligence()
    )

    private fun determineHealth(highSeverity: Int, active: Int, total: Int): SystemHealth {
        return when {
            highSeverity > 5 || (total > 0 && active < total / 2) -> SystemHealth.CRITICAL
            highSeverity > 0 || (total > 0 && active < total) -> SystemHealth.DEGRADED
            else -> SystemHealth.STABLE
        }
    }

    init {
        intelligence.onEach {
            Timber.tag("KAVACH_INTEL").d("Aggregated Update: ${it.activeIncidentsCount} Incidents, ${it.unreadBroadcastsCount} Unread")
        }.launchIn(scope)
    }
}

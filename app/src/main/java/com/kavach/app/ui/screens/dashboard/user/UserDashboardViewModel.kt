package com.kavach.app.ui.screens.dashboard.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.dao.OrderDao
import com.kavach.app.data.local.dao.SosDao
import com.kavach.app.data.local.entity.BroadcastEntity
import com.kavach.app.data.local.entity.OrderEntity
import com.kavach.app.data.repository.OrderRepository
import com.kavach.app.data.repository.BroadcastRepository
import com.kavach.app.util.NetworkMonitor
import com.kavach.app.util.ConnectionStatus
import com.kavach.app.data.remote.websocket.WebSocketManager
import com.kavach.app.data.remote.websocket.WsEvent
import com.kavach.app.data.remote.worker.SosWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

/**
 * UserDashboardViewModel — Mission Execution runtime.
 *
 * CRITICAL ISOLATION RULES:
 * 1. NEVER injects PilotOpsAggregator — that is Pilot/Admin only.
 * 2. NEVER queries global personnel, incidents, delivery analytics.
 * 3. ALL queries scoped to currentUserPno only.
 * 4. Metrics are personal mission status — not fleet intelligence.
 *
 * Data Sources:
 *   - OrderDao.observeAllOrders() filtered client-side to unacknowledged
 *   - BroadcastDao.observeAllBroadcasts() filtered to unread
 *   - SessionDataStore for pno, name, connectivity
 */
data class UserMissionMetrics(
    val pendingOrders: Int = 0,          // My orders I haven't acknowledged
    val unreadBroadcasts: Int = 0,       // Broadcasts I haven't read
    val emergencyBroadcasts: Int = 0,    // High-priority broadcasts
    val overdueOrders: Int = 0,          // Orders past due date (if available)
)

data class UserDashboardState(
    val name: String = "",
    val pno: String = "",
    val unit: String = "",
    val metrics: UserMissionMetrics = UserMissionMetrics(),
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val wsConnected: Boolean = false,
    val hasEmergencyBroadcast: Boolean = false,
    val emergencyTitle: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDataStore: SessionDataStore,
    private val orderDao: OrderDao,
    private val broadcastDao: BroadcastDao,
    private val orderRepository: OrderRepository,
    private val broadcastRepository: BroadcastRepository,
    private val sosDao: SosDao,
    networkMonitor: NetworkMonitor,
    private val wsManager: WebSocketManager
) : ViewModel() {

    val connectionStatus = networkMonitor.status

    private val wsConnected: StateFlow<Boolean> = wsManager.events
        .filterIsInstance<WsEvent.StateChanged>()
        .map { it.state == WebSocketManager.ConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val state: StateFlow<UserDashboardState> = combine(
        sessionDataStore.name,
        sessionDataStore.pno,
        sessionDataStore.unit,
        networkMonitor.status,
        wsConnected
    ) { name, pno, unit, connStatus, ws ->
        Triple(Triple(name, pno, unit), connStatus, ws)
    }.flatMapLatest { (identity, connStatus, ws) ->
        val (name, pno, unit) = identity

        // Scoped DB queries — personal data only
        combine(
            orderDao.observeAllOrders(),
            broadcastDao.observeAllBroadcasts()
        ) { orders, broadcasts ->
            val pendingOrders = orders.count { it.status == "PENDING" }
            val unreadBroadcasts = broadcasts.count { it.status != "READ" && it.status != "ACKNOWLEDGED" }
            val emergencyBroadcasts = broadcasts.count {
                (it.priority == "HIGH" || it.priority == "CRITICAL" || it.priority == "URGENT") &&
                it.status != "ACKNOWLEDGED"
            }
            val emergencyBroadcast = broadcasts.firstOrNull {
                (it.priority == "CRITICAL" || it.priority == "URGENT") &&
                it.status != "ACKNOWLEDGED"
            }

            UserDashboardState(
                name = name ?: "Officer",
                pno = pno ?: "",
                unit = unit ?: "",
                metrics = UserMissionMetrics(
                    pendingOrders = pendingOrders,
                    unreadBroadcasts = unreadBroadcasts,
                    emergencyBroadcasts = emergencyBroadcasts
                ),
                isOnline = connStatus == ConnectionStatus.AVAILABLE,
                wsConnected = ws,
                hasEmergencyBroadcast = emergencyBroadcast != null,
                emergencyTitle = emergencyBroadcast?.title ?: ""
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserDashboardState()
    )

    fun triggerSos() {
        viewModelScope.launch {
            try {
                val pno = sessionDataStore.pno.firstOrNull() ?: ""
                val unit = sessionDataStore.unit.firstOrNull() ?: ""
                
                val localId = java.util.UUID.randomUUID().toString()
                val correlationId = java.util.UUID.randomUUID().toString()
                
                // 1. Local DB commit (instant, process-death safe)
                val sos = com.kavach.app.data.local.entity.SosEntity(
                    localId = localId,
                    correlationId = correlationId,
                    senderPno = pno,
                    senderUnit = unit,
                    message = "SOS — IMMEDIATE FIELD ASSISTANCE REQUIRED",
                    status = "QUEUED",
                    createdAt = System.currentTimeMillis()
                )
                sosDao.insertSos(sos)
                Timber.i("UserDashboardViewModel: SOS drafted locally. localId=$localId, correlationId=$correlationId")

                // 2. Schedule expedited worker
                SosWorker.schedule(context)
            } catch (e: java.lang.Exception) {
                Timber.e(e, "UserDashboardViewModel: Failed to trigger SOS")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                // Only refresh what the user needs — no global pulls
                launch { orderRepository.refreshOrders() }
                launch { broadcastRepository.refreshBroadcasts() }
            } catch (e: Exception) {
                Timber.e(e, "UserDashboard refresh failed")
            }
        }
    }

    init {
        refresh()
    }
}

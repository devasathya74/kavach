package com.kavach.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.repository.OrderRepository
import com.kavach.app.data.repository.MissionRepository
import com.kavach.app.domain.model.Order
import com.kavach.app.utils.ApiResult
import com.kavach.app.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.kavach.app.data.remote.websocket.WebSocketManager
import javax.inject.Inject

data class DashboardMetrics(
    val personnelCount : Int = 0,
    val incidentCount  : Int = 0,
    val broadcastCount : Int = 0,
    val approvalCount  : Int = 0,
    val orderCount     : Int = 0,
    val trainingCount  : Int = 0,
    val activePilots   : Int = 0
)

data class DashboardUiState(
    val orders          : List<Order> = emptyList(),
    val metrics         : DashboardMetrics = DashboardMetrics(),
    val isOrdersLoading : Boolean = false,
    val isMissionLoading: Boolean = false,
    val error           : String? = null,
    val isRefreshing    : Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val missionRepository: MissionRepository,
    private val broadcastRepository: com.kavach.app.data.repository.BroadcastRepository,
    private val userRepository: com.kavach.app.data.repository.UserManagementRepository,
    private val aggregator: com.kavach.app.core.intelligence.PilotOpsAggregator,
    val sessionDataStore: SessionDataStore,
    private val wsManager: WebSocketManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val connectionStatus = networkMonitor.status

    val wsState: StateFlow<WebSocketManager.ConnectionState> = wsManager.events
        .filterIsInstance<com.kavach.app.data.remote.websocket.WsEvent.StateChanged>()
        .map { it.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WebSocketManager.ConnectionState.DISCONNECTED)

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        aggregator.intelligence,
        _isRefreshing
    ) { intel, refreshing ->
        DashboardUiState(
            metrics = DashboardMetrics(
                personnelCount = intel.totalPersonnelCount,
                incidentCount = intel.activeIncidentsCount,
                broadcastCount = intel.unreadBroadcastsCount,
                approvalCount = intel.pendingApprovalsCount,
                orderCount = intel.unacknowledgedOrdersCount,
                activePilots = intel.activeDevicesCount
            ),
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun refresh() = viewModelScope.launch {
        _isRefreshing.value = true
        
        // Parallel refresh from network to local cache
        launch { orderRepository.refreshOrders() }
        launch { broadcastRepository.refreshBroadcasts() }
        launch { userRepository.refreshUsers() }
        // Incident refresh logic can be added here once missionRepository is hardened
        
        _isRefreshing.value = false
    }
}

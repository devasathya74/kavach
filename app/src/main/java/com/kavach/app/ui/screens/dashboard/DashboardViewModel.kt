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
import javax.inject.Inject

data class DashboardUiState(
    val orders         : List<Order> = emptyList(),
    val incidentCount  : Int  = 0,
    val broadcastCount : Int  = 0,
    val trainingCount  : Int  = 0,
    val orderCount     : Int  = 0,
    val isOrdersLoading: Boolean = false,
    val isMissionLoading: Boolean = false,
    val error          : String? = null,
    val isRefreshing   : Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val missionRepository: MissionRepository,
    val sessionDataStore: SessionDataStore,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val connectionStatus = networkMonitor.status

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = combine(
        orderRepository.getAllOrders(),
        _uiState
    ) { orders, state ->
        state.copy(
            orders = orders.filter { !it.isAcknowledged }.take(3),
            isOrdersLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    // Note: Automatic refresh removed from init for "Offline-First" stability.
    // Dashboard will load from local Room cache immediately.
    // UI can call refresh() manually or it can be triggered by a sync worker.

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isRefreshing = true, isMissionLoading = true)
        
        // Parallel refresh from network to local cache
        launch { 
            orderRepository.refreshOrders() 
        }
        
        launch {
            val incidents = missionRepository.getIncidents()
            val broadcasts = missionRepository.getBroadcasts()
            
            val incCount = if (incidents is ApiResult.Success) incidents.data.size else 0
            val brCount = if (broadcasts is ApiResult.Success) broadcasts.data.count { !it.acknowledged } else 0
            
            _uiState.value = _uiState.value.copy(
                incidentCount = incCount,
                broadcastCount = brCount,
                isMissionLoading = false,
                isRefreshing = false
            )
        }
    }
}

package com.kavach.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.repository.OrderRepository
import com.kavach.app.data.repository.MissionRepository
import com.kavach.app.domain.model.Order
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val orders: List<Order> = emptyList(),
    val incidentCount: Int = 0,
    val broadcastCount: Int = 0,
    val isOrdersLoading: Boolean = false,
    val isMissionLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val missionRepository: MissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = combine(
        orderRepository.getAllOrders(),
        _uiState
    ) { orders, state ->
        state.copy(
            orders = orders.filter { !it.isAcknowledged }.take(3),
            isOrdersLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState(isOrdersLoading = true, isMissionLoading = true))

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isRefreshing = true, isMissionLoading = true)
        
        // Parallel refresh for orders and mission data
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

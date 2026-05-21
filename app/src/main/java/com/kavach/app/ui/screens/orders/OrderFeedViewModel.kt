package com.kavach.app.ui.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.repository.OrderRepository
import com.kavach.app.domain.model.Order
import com.kavach.app.utils.OperationalActionState
import com.kavach.app.utils.OperationalUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderFeedState(
    val uiState: OperationalUiState<List<Order>> = OperationalUiState.Idle,
    val actionState: OperationalActionState = OperationalActionState.Idle,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class OrderFeedViewModel @Inject constructor(
    private val repository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrderFeedState())
    val state = _state.asStateFlow()

    init {
        observeOrders()
        refreshOrders()
    }

    private fun observeOrders() {
        repository.observeOrders()
            .onEach { orders ->
                val newState = if (orders.isEmpty() && _state.value.uiState is OperationalUiState.Idle) {
                    OperationalUiState.Loading
                } else {
                    OperationalUiState.Success(orders)
                }
                _state.update { it.copy(uiState = newState) }
            }
            .launchIn(viewModelScope)
    }

    fun refreshOrders() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            repository.refreshOrders()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun acknowledgeOrder(localId: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = OperationalActionState.Processing) }
            repository.acknowledgeOrder(localId)
            _state.update { it.copy(actionState = OperationalActionState.Success("Order Acknowledged")) }
        }
    }

    fun clearActionState() {
        _state.update { it.copy(actionState = OperationalActionState.Idle) }
    }
}

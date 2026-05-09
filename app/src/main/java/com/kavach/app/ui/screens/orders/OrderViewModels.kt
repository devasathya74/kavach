package com.kavach.app.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.repository.OrderRepository
import com.kavach.app.domain.model.Order
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Order List ────────────────────────────────────────────

data class OrderListUiState(
    val orders    : List<Order> = emptyList(),
    val isLoading : Boolean = false,
    val error     : String? = null
)

@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val repository: OrderRepository
) : ViewModel() {

    val uiState: StateFlow<OrderListUiState> = repository.getAllOrders()
        .map { OrderListUiState(orders = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrderListUiState(isLoading = true))

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        repository.refreshOrders()
    }
}

// ── Order Detail ──────────────────────────────────────────

data class OrderDetailUiState(
    val order          : Order? = null,
    val viewerPno      : String = "",
    val isLoading      : Boolean = true,
    val isAcknowledging: Boolean = false,
    val acknowledged   : Boolean = false,
    val error          : String? = null
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val repository : OrderRepository,
    private val sessionDataStore: com.kavach.app.data.local.SessionDataStore,
    savedStateHandle       : SavedStateHandle
) : ViewModel() {

    private val orderId = savedStateHandle.get<String>("orderId") ?: ""

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    init { loadOrder() }

    private fun loadOrder() = viewModelScope.launch {
        val pno = sessionDataStore.pno.firstOrNull() ?: "UNKNOWN"
        _uiState.value = OrderDetailUiState(isLoading = true, viewerPno = pno)
        when (val result = repository.getOrderById(orderId)) {
            is ApiResult.Success -> _uiState.value = OrderDetailUiState(
                order = result.data, isLoading = false,
                acknowledged = result.data.isAcknowledged,
                viewerPno = pno
            )
            is ApiResult.Error   -> _uiState.value = OrderDetailUiState(isLoading = false, error = result.message)
            else -> {}
        }
    }

    fun acknowledgeOrder(readDuration: Long) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isAcknowledging = true)
        when (repository.acknowledgeOrder(orderId, readDuration)) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                isAcknowledging = false, acknowledged = true
            )
            is ApiResult.Error   -> _uiState.value = _uiState.value.copy(
                isAcknowledging = false, error = "Acknowledgment failed"
            )
            else -> {}
        }
    }
}

package com.kavach.app.ui.screens.user.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.local.dao.OrderDao
import com.kavach.app.data.local.entity.OrderEntity
import com.kavach.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OrderTab { PENDING, ACKNOWLEDGED, OVERDUE }

data class MyOrdersState(
    val orders: List<OrderEntity> = emptyList(),
    val selectedTab: OrderTab = OrderTab.PENDING,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val actionDone: String? = null
) {
    val pending: List<OrderEntity>
        get() = orders.filter { it.status == "PENDING" }

    val acknowledged: List<OrderEntity>
        get() = orders.filter { it.status == "ACKNOWLEDGED" }

    val overdue: List<OrderEntity>
        get() = orders.filter {
            it.status == "PENDING" && it.expiresAt != null && it.expiresAt < System.currentTimeMillis()
        }

    val displayList: List<OrderEntity>
        get() = when (selectedTab) {
            OrderTab.PENDING -> pending
            OrderTab.ACKNOWLEDGED -> acknowledged
            OrderTab.OVERDUE -> overdue
        }
}

/**
 * MyOrdersViewModel — Mission Execution.
 *
 * Scoped: observeAllOrders() (all orders in local DB are already
 * synced for this user — the sync filter is on the server side).
 * Never queries all orders globally.
 */
@HiltViewModel
class MyOrdersViewModel @Inject constructor(
    private val orderDao: OrderDao,
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MyOrdersState())
    val state: StateFlow<MyOrdersState> = _state.asStateFlow()

    init {
        observeOrders()
        refresh()
    }

    private fun observeOrders() {
        viewModelScope.launch {
            orderDao.observeAllOrders()
                .catch { e -> _state.update { it.copy(error = e.localizedMessage) } }
                .collect { orders ->
                    _state.update { it.copy(orders = orders, isLoading = false) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try { orderRepository.refreshOrders() } catch (e: Exception) { /* offline OK */ }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectTab(tab: OrderTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun acknowledgeOrder(localId: String) {
        viewModelScope.launch {
            orderRepository.acknowledgeOrder(localId)
            _state.update { it.copy(actionDone = "Order Acknowledged") }
        }
    }

    fun clearActionDone() {
        _state.update { it.copy(actionDone = null) }
    }
}

package com.kavach.app.ui.screens.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.entity.BroadcastWithStats
import com.kavach.app.data.repository.BroadcastRepository
import com.kavach.app.utils.OperationalActionState
import com.kavach.app.utils.OperationalUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BroadcastFeedState(
    val uiState: OperationalUiState<List<BroadcastWithStats>> = OperationalUiState.Idle,
    val actionState: OperationalActionState = OperationalActionState.Idle,
    val isRefreshing: Boolean = false,
    val selectedFilter: BroadcastFilter = BroadcastFilter.ALL
)

enum class BroadcastFilter {
    ALL, EMERGENCY, UNREAD, UNACKNOWLEDGED
}

@HiltViewModel
class BroadcastViewModel @Inject constructor(
    private val repository: BroadcastRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BroadcastFeedState())
    val state = _state.asStateFlow()

    init {
        observeBroadcasts()
        refresh()
    }

    private fun observeBroadcasts() {
        repository.observeBroadcastsWithStats()
            .onEach { broadcasts ->
                val filtered = when (_state.value.selectedFilter) {
                    BroadcastFilter.ALL -> broadcasts
                    BroadcastFilter.EMERGENCY -> broadcasts.filter { it.broadcast.priority == "HIGH" }
                    BroadcastFilter.UNREAD -> broadcasts.filter { it.readCount == 0 } // Simplified logic
                    BroadcastFilter.UNACKNOWLEDGED -> broadcasts.filter { it.ackCount == 0 }
                }
                
                val newState = if (filtered.isEmpty() && _state.value.uiState is OperationalUiState.Idle) {
                    OperationalUiState.Loading
                } else {
                    OperationalUiState.Success(filtered)
                }
                _state.update { it.copy(uiState = newState) }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            repository.refreshBroadcasts()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun markAsRead(broadcastId: String) {
        viewModelScope.launch {
            repository.markAsRead(broadcastId)
        }
    }

    fun acknowledge(broadcastId: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = OperationalActionState.Processing) }
            repository.acknowledgeBroadcast(broadcastId)
            _state.update { it.copy(actionState = OperationalActionState.Success("Acknowledged")) }
        }
    }

    fun setFilter(filter: BroadcastFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        // Re-trigger observation logic (already handled by flow collection if we combine with filter state)
    }

    fun clearActionState() {
        _state.update { it.copy(actionState = OperationalActionState.Idle) }
    }
}

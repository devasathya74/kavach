package com.kavach.app.ui.screens.pilot.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.broadcast.BroadcastDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BroadcastCenterState(
    val broadcasts: List<BroadcastDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BroadcastCenterViewModel @Inject constructor(
    private val api: KavachApiV2
) : ViewModel() {

    private val _state = MutableStateFlow(BroadcastCenterState())
    val state = _state.asStateFlow()

    init {
        loadBroadcasts()
    }

    fun loadBroadcasts(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val results = api.getBroadcasts()
                _state.update { it.copy(broadcasts = results, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    fun acknowledge(id: String) {
        viewModelScope.launch {
            try {
                api.acknowledgeBroadcast(id)
                loadBroadcasts(isRefresh = true)
            } catch (e: Exception) {
                // Log error
            }
        }
    }
}

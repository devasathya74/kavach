package com.kavach.app.ui.screens.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.broadcast.BroadcastDto
import com.kavach.app.data.repository.MissionRepository
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BroadcastInboxUiState(
    val broadcasts: List<BroadcastDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAcknowledgeLoading: Boolean = false
)

@HiltViewModel
class BroadcastInboxViewModel @Inject constructor(
    private val repository: MissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BroadcastInboxUiState())
    val uiState: StateFlow<BroadcastInboxUiState> = _uiState.asStateFlow()

    init {
        loadBroadcasts()
    }

    fun loadBroadcasts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getBroadcasts()
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        broadcasts = result.data,
                        isLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
                else -> {}
            }
        }
    }

    fun acknowledge(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAcknowledgeLoading = true)
            val result = repository.acknowledgeBroadcast(id)
            if (result is ApiResult.Success) {
                loadBroadcasts()
            }
            _uiState.value = _uiState.value.copy(isAcknowledgeLoading = false)
        }
    }
}

package com.kavach.app.ui.screens.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.incident.IncidentDto
import com.kavach.app.data.repository.MissionRepository
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentFeedUiState(
    val incidents: List<IncidentDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val selectedSeverity: String? = null
)

@HiltViewModel
class IncidentFeedViewModel @Inject constructor(
    private val repository: MissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncidentFeedUiState())
    val uiState: StateFlow<IncidentFeedUiState> = _uiState.asStateFlow()

    init {
        loadIncidents()
    }

    fun loadIncidents(isRefreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !isRefreshing,
                isRefreshing = isRefreshing,
                error = null
            )
            val result = repository.getIncidents(
                severity = _uiState.value.selectedSeverity
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        incidents = result.data,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
                else -> {}
            }
        }
    }

    fun setSeverityFilter(severity: String?) {
        _uiState.value = _uiState.value.copy(selectedSeverity = severity)
        loadIncidents()
    }
}

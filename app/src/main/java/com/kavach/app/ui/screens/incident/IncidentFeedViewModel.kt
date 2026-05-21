package com.kavach.app.ui.screens.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import com.kavach.app.data.remote.dto.incident.IncidentDto
import com.kavach.app.data.repository.MissionRepository
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.kavach.app.data.repository.IncidentRepository
import com.kavach.app.domain.model.Incident
import com.kavach.app.utils.OperationalUiState
import com.kavach.app.utils.OperationalActionState
import kotlinx.coroutines.flow.*

data class IncidentFeedUiState(
    val uiState: OperationalUiState<List<Incident>> = OperationalUiState.Idle,
    val actionState: OperationalActionState = OperationalActionState.Idle,
    val isRefreshing: Boolean = false,
    val selectedSeverity: String? = null
)

@HiltViewModel
class IncidentFeedViewModel @Inject constructor(
    private val repository: IncidentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(IncidentFeedUiState())
    val state = _state.asStateFlow()

    init {
        observeIncidents()
    }

    private fun observeIncidents() {
        repository.observeIncidents()
            .onEach { incidents ->
                val filtered = if (_state.value.selectedSeverity != null) {
                    incidents.filter { it.severity == _state.value.selectedSeverity }
                } else incidents
                
                _state.update { 
                    it.copy(uiState = OperationalUiState.Success(filtered)) 
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadIncidents(isRefreshing: Boolean = false) {
        if (isRefreshing) {
            _state.update { it.copy(isRefreshing = true) }
        } else if (_state.value.uiState !is OperationalUiState.Success) {
            _state.update { it.copy(uiState = OperationalUiState.Loading) }
        }
        
        // Note: Actual sync is handled by background worker or explicit pull
        // For now, we rely on the DB observation and background workers.
        viewModelScope.launch {
            // Simulated pull from server if needed
            delay(500) 
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun setSeverityFilter(severity: String?) {
        _state.update { it.copy(selectedSeverity = severity) }
    }

    fun clearActionState() {
        _state.update { it.copy(actionState = OperationalActionState.Idle) }
    }
}

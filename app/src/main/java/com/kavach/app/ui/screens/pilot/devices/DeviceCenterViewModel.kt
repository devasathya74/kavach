package com.kavach.app.ui.screens.pilot.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.personnel.OfficerDto
import com.kavach.app.data.repository.UserManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kavach.app.utils.onSuccess
import com.kavach.app.utils.onFailure
import com.kavach.app.data.remote.dto.v2.OfficerDeviceDto

import com.kavach.app.utils.OperationalUiState
import com.kavach.app.utils.OperationalActionState
import com.kavach.app.domain.model.DeviceStatus
import com.kavach.app.utils.onSuccess
import com.kavach.app.utils.onFailure

data class DeviceCenterState(
    val uiState: OperationalUiState<List<DeviceListItemUiModel>> = OperationalUiState.Idle,
    val actionState: OperationalActionState = OperationalActionState.Idle,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val statusFilter: String? = null
)

@HiltViewModel
class DeviceCenterViewModel @Inject constructor(
    private val repository: UserManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceCenterState())
    val state = _state.asStateFlow()

    init {
        observeDevices()
        syncDevices()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeDevices() {
        val filterFlow = _state.map { it.searchQuery to it.statusFilter }.distinctUntilChanged()
        
        filterFlow.flatMapLatest { (query, status) ->
            repository.observeAllDevices(
                search = query.ifBlank { null },
                status = status
            )
        }.map { entities ->
            val uiModels = entities.map { it.toUiModel() }
            if (uiModels.isEmpty() && _state.value.uiState is OperationalUiState.Idle) {
                OperationalUiState.Loading
            } else {
                OperationalUiState.Success(uiModels)
            }
        }.onEach { newState ->
            _state.update { it.copy(uiState = newState) }
        }.launchIn(viewModelScope)
    }

    fun syncDevices() {
        viewModelScope.launch {
            if (_state.value.uiState !is OperationalUiState.Success) {
                _state.update { it.copy(uiState = OperationalUiState.Loading) }
            }
            
            repository.getDevices(
                search = _state.value.searchQuery.ifBlank { null },
                status = _state.value.statusFilter
            ).onSuccess {
                _state.update { it.copy(isRefreshing = false) }
            }.onFailure { e ->
                _state.update { 
                    it.copy(
                        uiState = OperationalUiState.Error(e.message ?: "Failed to sync devices"),
                        isRefreshing = false
                    ) 
                }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onFilterChange(status: String?) {
        _state.update { it.copy(statusFilter = status) }
    }

    fun onRefresh() {
        _state.update { it.copy(isRefreshing = true) }
        syncDevices()
    }

    fun clearActionState() {
        _state.update { it.copy(actionState = OperationalActionState.Idle) }
    }

    fun revokeDevice(officerId: String, deviceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = OperationalActionState.Processing) }
            
            repository.revokeDevice(officerId, deviceId)
                .onSuccess {
                    _state.update { it.copy(actionState = OperationalActionState.Success("Device revoked successfully")) }
                    syncDevices()
                }
                .onFailure { e ->
                    _state.update { it.copy(actionState = OperationalActionState.Error(e.message ?: "Revoke failed")) }
                }
        }
    }

    fun blockDevice(officerId: String, deviceId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = OperationalActionState.Processing) }
            
            // Reusing UserManagement update for blocking logic (status = BLOCKED)
            repository.updateUser(officerId, com.kavach.app.data.remote.dto.v2.UpdateUserRequest(isActive = false))
                .onSuccess {
                    _state.update { it.copy(actionState = OperationalActionState.Success("Device/User blocked")) }
                }
                .onFailure { e ->
                    _state.update { it.copy(actionState = OperationalActionState.Error(e.message ?: "Block failed")) }
                }
        }
    }
}

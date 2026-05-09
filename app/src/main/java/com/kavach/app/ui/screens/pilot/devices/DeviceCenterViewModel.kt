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

data class DeviceCenterState(
    val devices: List<OfficerDeviceDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
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
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Note: Repository needs getDevices implemented for the general dashboard
            // For now, using a placeholder logic or updating repository
            repository.getDevices(
                search = _state.value.searchQuery,
                status = _state.value.statusFilter
            ).onSuccess { devices ->
                _state.update { it.copy(devices = devices, isLoading = false, error = null) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadDevices()
    }

    fun onRefresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadDevices()
        _state.update { it.copy(isRefreshing = false) }
    }

    fun revokeDevice(officerId: String, deviceId: String) {
        viewModelScope.launch {
            repository.revokeDevice(officerId, deviceId).onSuccess {
                loadDevices()
            }
        }
    }
}

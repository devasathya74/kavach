package com.kavach.app.ui.screens.pilot.personnel

import androidx.lifecycle.SavedStateHandle
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

data class OfficerDetailState(
    val officer: OfficerDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRevoking: Boolean = false
)

@HiltViewModel
class OfficerDetailViewModel @Inject constructor(
    private val repository: UserManagementRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val officerId: String = checkNotNull(savedStateHandle["userId"])

    private val _state = MutableStateFlow(OfficerDetailState())
    val state = _state.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // For now, simplicity: direct API call for detail screen hydration
            // We'll move to observing Room flow in the next refinement
            try {
                val result = repository.getUserDetailNetwork(officerId)
                result.onSuccess { officer ->
                    _state.update { it.copy(officer = officer, isLoading = false, error = null) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deactivateUser(reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.deactivateUser(officerId, reason).onSuccess {
                loadDetail()
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun globalLogout() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.globalLogout(officerId).onSuccess {
                loadDetail()
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isRevoking = true) }
            repository.revokeDevice(officerId, deviceId).onSuccess {
                loadDetail() // Refresh
            }
            _state.update { it.copy(isRevoking = false) }
        }
    }
}

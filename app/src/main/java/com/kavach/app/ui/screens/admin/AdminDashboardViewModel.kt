package com.kavach.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.AdminOfficerDto
import com.kavach.app.data.remote.dto.SuspiciousSessionDto
import com.kavach.app.data.remote.repository.AdminRepository
import com.kavach.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardUiState(
    val isLoading          : Boolean                   = false,
    val error              : String?                   = null,
    val officers           : List<AdminOfficerDto>     = emptyList(),
    val suspiciousSessions : List<SuspiciousSessionDto> = emptyList(),
    val isActionLoading    : Boolean                   = false,
    val actionMessage      : String?                   = null,
    val selectedTab        : Int                       = 0
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun loadAll() {
        loadOfficers()
        loadSuspicious()
    }

    fun loadOfficers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = adminRepository.getOfficers()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        officers  = result.data ?: emptyList()
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error     = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun loadSuspicious() {
        viewModelScope.launch {
            when (val result = adminRepository.getSuspiciousSessions()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        suspiciousSessions = result.data ?: emptyList()
                    )
                }
                is Resource.Error -> { }
                else -> {}
            }
        }
    }

    fun performAction(pno: String, action: String, reason: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, actionMessage = null)
            when (val result = adminRepository.performAction(pno, action, reason)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        actionMessage   = "Action successful"
                    )
                    loadAll()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        actionMessage   = "Failed: ${result.message}"
                    )
                }
                else -> {}
            }
        }
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }
}

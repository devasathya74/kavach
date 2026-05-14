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
    val isRevoking: Boolean = false,
    val isSuccess: Boolean = false,
    val canEdit: Boolean = false,
    val canManageAuthority: Boolean = false
)

@HiltViewModel
class OfficerDetailViewModel @Inject constructor(
    private val repository: UserManagementRepository,
    private val sessionDataStore: com.kavach.app.data.local.SessionDataStore,
    private val authEngine: com.kavach.app.core.security.AuthorizationEngine,
    private val networkMonitor: com.kavach.app.util.NetworkMonitor,
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
                    val myRole = com.kavach.app.core.auth.SystemRole.fromString(sessionDataStore.role.first())
                    val myRankLevel = sessionDataStore.rankLevel.first()
                    val targetRankLevel = officer.profile?.rank?.level ?: 0
                    val targetRole = com.kavach.app.core.auth.SystemRole.fromString(officer.role)
                    
                    val canEdit = when {
                        myRole == com.kavach.app.core.auth.SystemRole.ADMIN -> true // ADMIN bypass
                        myRole == com.kavach.app.core.auth.SystemRole.PILOT -> {
                            if (targetRole == com.kavach.app.core.auth.SystemRole.PILOT) myRankLevel > targetRankLevel
                            else targetRole == com.kavach.app.core.auth.SystemRole.USER // Pilot manages User
                        }
                        else -> false
                    }
                    
                    _state.update { it.copy(
                        officer = officer, 
                        isLoading = false, 
                        error = null,
                        canEdit = canEdit,
                        canManageAuthority = canEdit
                    ) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deactivateUser() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.deleteUser(officerId) // This is now a soft delete (is_active=false)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isSuccess = true) }
                    loadDetail()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun globalLogout() {
        viewModelScope.launch {
            val role = sessionDataStore.role.first() ?: "USER"
            val isOffline = networkMonitor.status.first() == com.kavach.app.util.ConnectionStatus.UNAVAILABLE
            authEngine.requestAuthorization(
                action = com.kavach.app.core.security.CriticalAction.FORCE_LOGOUT_USER,
                actorRole = role,
                isOffline = isOffline,
                onResolved = { granted ->
                    if (granted) {
                        viewModelScope.launch {
                            _state.update { it.copy(isLoading = true) }
                            repository.globalLogout(officerId).onSuccess {
                                loadDetail()
                            }.onFailure { e ->
                                _state.update { it.copy(isLoading = false, error = e.message) }
                            }
                        }
                    }
                }
            )
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            val role = sessionDataStore.role.first() ?: "USER"
            val isOffline = networkMonitor.status.first() == com.kavach.app.util.ConnectionStatus.UNAVAILABLE
            authEngine.requestAuthorization(
                action = com.kavach.app.core.security.CriticalAction.REVOKE_DEVICE,
                actorRole = role,
                isOffline = isOffline,
                onResolved = { granted ->
                    if (granted) {
                        viewModelScope.launch {
                            _state.update { it.copy(isRevoking = true) }
                            repository.revokeDevice(officerId, deviceId).onSuccess {
                                loadDetail()
                            }
                            _state.update { it.copy(isRevoking = false) }
                        }
                    }
                }
            )
        }
    }
}

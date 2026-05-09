package com.kavach.app.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.security.AttestationResult
import com.kavach.app.security.IntegrityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceStatusUiState(
    val integrityLevel: String = "UNKNOWN",
    val isAttesting: Boolean = false,
    val lastCheck: Long = 0,
    val error: String? = null,
    val deviceId: String = ""
)

@HiltViewModel
class DeviceStatusViewModel @Inject constructor(
    private val sessionStore: SessionDataStore,
    private val integrityRepository: IntegrityRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = combine(
        sessionStore.integrityLevel,
        _uiState
    ) { level, state ->
        state.copy(
            integrityLevel = level ?: "UNKNOWN",
            deviceId = com.kavach.app.utils.DeviceIdUtil.getDeviceId(context)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceStatusUiState())

    fun checkIntegrity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAttesting = true, error = null)
            val result = integrityRepository.attest()
            when (result) {
                is AttestationResult.Passed -> {
                    sessionStore.saveIntegrityLevel(result.verdict.integrityLevel ?: "BASIC")
                    _uiState.value = _uiState.value.copy(lastCheck = System.currentTimeMillis())
                }
                is AttestationResult.Restricted -> {
                    sessionStore.saveIntegrityLevel(result.level)
                }
                is AttestationResult.Failed -> {
                    _uiState.value = _uiState.value.copy(error = result.reason)
                }
                is AttestationResult.Degraded -> {
                    _uiState.value = _uiState.value.copy(error = "Network Error: ${result.reason}")
                }
            }
            _uiState.value = _uiState.value.copy(isAttesting = false)
        }
    }
}

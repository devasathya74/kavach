package com.kavach.app.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore

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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            integrityLevel = "STABLE",
            deviceId = com.kavach.app.utils.DeviceIdUtil.getDeviceId(context)
        )
    }

    fun checkIntegrity() {
        // Deprecated during stabilization phase: Play Integrity runtime removed.
        // Keeping as no-op to avoid breaking UI references.
    }
}

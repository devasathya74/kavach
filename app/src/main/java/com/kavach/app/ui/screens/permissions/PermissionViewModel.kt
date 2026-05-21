package com.kavach.app.ui.screens.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionUiState(
    val hasRequiredPermissions: Boolean = false,
    val isChecking: Boolean = true,
    val isPermanentlyDenied: Boolean = false
)

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val sessionStore: SessionDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        val mandatory = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mandatory.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = mandatory.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        // Note: isPermanentlyDenied should ideally be checked against activity rationale.
        // For now, we update it from the UI or based on a failed request.
        _uiState.value = _uiState.value.copy(
            hasRequiredPermissions = allGranted,
            isChecking = false
        )
    }

    fun setPermanentlyDenied(denied: Boolean) {
        _uiState.value = _uiState.value.copy(isPermanentlyDenied = denied)
    }

    fun markPermissionsAsHandled() {
        viewModelScope.launch {
            sessionStore.savePermissionsHandled()
        }
    }
}

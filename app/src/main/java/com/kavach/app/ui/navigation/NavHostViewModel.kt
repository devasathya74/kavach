package com.kavach.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel that handles session monitoring and recovery for KavachNavHost. */
@HiltViewModel
class NavHostViewModel @Inject constructor(
    val sessionDataStore : SessionDataStore,
    private val authRepo   : AuthRepository,
    networkMonitor         : com.kavach.app.util.NetworkMonitor
) : ViewModel() {

    val connectionStatus = networkMonitor.status

    private val _isRecovering = MutableStateFlow(false)
    val isRecovering = _isRecovering.asStateFlow()

    val isVerifiedInThisSession = sessionDataStore.isVerifiedInThisSession

    private val _isLimitedMode = MutableStateFlow(false)
    val isLimitedMode = _isLimitedMode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    suspend fun syncProfile(): Boolean {
        if (_isRecovering.value) return false
        
        return try {
            _isRecovering.value = true
            _error.value = null
            authRepo.syncProfile()
            sessionDataStore.markAsVerified()
            _isLimitedMode.value = false
            true
        } catch (e: Exception) {
            _error.value = "Network Error: Unable to verify role"
            false
        } finally {
            _isRecovering.value = false
        }
    }

    fun enterLimitedMode() {
        _isLimitedMode.value = true
        _isRecovering.value = false
        _error.value = "सीमित मोड सक्रिय है (Limited Mode Active)"
    }

    fun manualRetry() {
        viewModelScope.launch {
            syncProfile()
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionDataStore.markAsUnverified()
            sessionDataStore.clearSession()
        }
    }
}

package com.kavach.app.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.core.security.AppLockManager
import com.kavach.app.core.security.PinManager
import com.kavach.app.data.local.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SecureAccessUiState(
    val pin: String = "",
    val error: String? = null,
    val attemptsRemaining: Int = 5,
    val isLockedOut: Boolean = false
)

@HiltViewModel
class SecureAccessViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val appLockManager: AppLockManager,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecureAccessUiState())
    val uiState: StateFlow<SecureAccessUiState> = _uiState.asStateFlow()


    fun onPinInput(digit: String) {
        if (_uiState.value.pin.length < 6) {
            val newPin = _uiState.value.pin + digit
            _uiState.value = _uiState.value.copy(pin = newPin, error = null)
            
            if (newPin.length == 6) {
                verifyPin(newPin)
            }
        }
    }

    fun onBackspace() {
        if (_uiState.value.pin.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(pin = _uiState.value.pin.dropLast(1))
        }
    }

    private fun verifyPin(pin: String) {
        if (pinManager.verifyPin(pin)) {
            unlock()
        } else {
            val newAttempts = _uiState.value.attemptsRemaining - 1
            if (newAttempts <= 0) {
                handleLockout()
            } else {
                _uiState.value = _uiState.value.copy(
                    pin = "",
                    attemptsRemaining = newAttempts,
                    error = "गलत PIN! आपके पास $newAttempts प्रयास शेष हैं।"
                )
            }
        }
    }

    private fun unlock() {
        appLockManager.unlock()
    }

    private fun handleLockout() {
        _uiState.value = _uiState.value.copy(isLockedOut = true, error = "अत्यधिक विफल प्रयास! सुरक्षा कारणों से सत्र बंद किया जा रहा है।")
        viewModelScope.launch {
            Timber.e("Secure Access: Lockout triggered after 5 failed attempts.")
            // Trigger threat escalation
            sessionDataStore.setSessionBreach("Security Lockout: 5 failed PIN attempts")
            delay(2000)
            sessionDataStore.clearSession()
        }
    }

    private suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
}

package com.kavach.app.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.core.security.PinManager
import com.kavach.app.data.local.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnrollmentUiState(
    val step: EnrollmentStep = EnrollmentStep.CREATE_PIN,
    val pin: String = "",
    val confirmPin: String = "",
    val error: String? = null,
    val isComplete: Boolean = false,
    val isBiometricAvailable: Boolean = false
)

enum class EnrollmentStep {
    CREATE_PIN,
    CONFIRM_PIN,
    SUCCESS
}

@HiltViewModel
class SecurityEnrollmentViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val sessionDataStore: SessionDataStore,
    private val biometricAuthManager: com.kavach.app.core.security.BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrollmentUiState(
        isBiometricAvailable = biometricAuthManager.isAvailable()
    ))
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    fun onPinInput(digit: String) {
        val currentState = _uiState.value
        if (currentState.step == EnrollmentStep.CREATE_PIN) {
            if (currentState.pin.length < 6) {
                _uiState.value = currentState.copy(pin = currentState.pin + digit, error = null)
            }
        } else if (currentState.step == EnrollmentStep.CONFIRM_PIN) {
            if (currentState.confirmPin.length < 6) {
                _uiState.value = currentState.copy(confirmPin = currentState.confirmPin + digit, error = null)
            }
        }
    }

    fun onBackspace() {
        val currentState = _uiState.value
        if (currentState.step == EnrollmentStep.CREATE_PIN) {
            if (currentState.pin.isNotEmpty()) {
                _uiState.value = currentState.copy(pin = currentState.pin.dropLast(1))
            }
        } else if (currentState.step == EnrollmentStep.CONFIRM_PIN) {
            if (currentState.confirmPin.isNotEmpty()) {
                _uiState.value = currentState.copy(confirmPin = currentState.confirmPin.dropLast(1))
            }
        }
    }

    fun nextStep() {
        val currentState = _uiState.value
        when (currentState.step) {
            EnrollmentStep.CREATE_PIN -> {
                if (validatePin(currentState.pin)) {
                    _uiState.value = currentState.copy(step = EnrollmentStep.CONFIRM_PIN)
                } else {
                    _uiState.value = currentState.copy(error = "कमज़ोर PIN! क्रमिक (sequential) या समान (repeated) अंकों का उपयोग न करें।")
                }
            }
            EnrollmentStep.CONFIRM_PIN -> {
                if (currentState.pin == currentState.confirmPin) {
                    savePin(currentState.pin)
                    completeEnrollment()
                } else {
                    _uiState.value = currentState.copy(confirmPin = "", error = "PIN मेल नहीं खाते। फिर से कोशिश करें।")
                }
            }
            else -> {}
        }
    }

    private fun validatePin(pin: String): Boolean {
        if (pin.length != 6) return false
        
        // No repeated digits (e.g. 111111)
        if (pin.all { it == pin[0] }) return false
        
        // No sequential digits (e.g. 123456, 654321)
        val sequential = "01234567890 09876543210"
        if (sequential.contains(pin)) return false
        
        return true
    }

    private fun savePin(pin: String) {
        viewModelScope.launch {
            pinManager.setPin(pin)
            sessionDataStore.setPinSet(true)
            // Note: We don't call completeEnrollment here anymore.
            // NavHost will still stay on this screen because biometricEnrollmentHandled is false.
        }
    }

    private fun completeEnrollment() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(step = EnrollmentStep.SUCCESS)
            kotlinx.coroutines.delay(1500)
            _uiState.value = _uiState.value.copy(isComplete = true)
        }
    }
}

package com.kavach.app.ui.screens.lock

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.core.security.AppLockManager
import com.kavach.app.core.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
    private val pinManager: PinManager
) : ViewModel() {

    private val _pinInput = MutableStateFlow("")
    val pinInput = _pinInput.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var isAuthenticating = false

    fun onPinChange(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _pinInput.value = value
            if (value.length == 6) {
                verifyPin(value)
            }
        }
    }

    private fun verifyPin(pin: String) {
        if (pinManager.verifyPin(pin)) {
            appLockManager.unlock()
        } else {
            _error.value = "अमान्य पिन (Invalid PIN)"
            _pinInput.value = ""
        }
    }

    fun showBiometricPrompt(activity: FragmentActivity) {
        if (isAuthenticating) return
        
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticating = false
                    appLockManager.unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isAuthenticating = false
                    // Don't show error if user cancelled
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        _error.value = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Keep isAuthenticating true as user might try again in the same prompt session
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("KAVACH सुरक्षित एक्सेस")
            .setSubtitle("बायोमेट्रिक का उपयोग कर अनलॉक करें")
            .setNegativeButtonText("PIN का उपयोग करें")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        isAuthenticating = true
        biometricPrompt.authenticate(promptInfo)
    }
}

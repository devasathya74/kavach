package com.kavach.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.repository.AuthRepository
import com.kavach.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading : Boolean = false,
    val error     : String? = null,
    val otpSent   : Boolean = false
)

data class OtpUiState(
    val isLoading : Boolean = false,
    val error     : String? = null,
    val verified  : Boolean = false
)

data class AdminLoginUiState(
    val isLoading    : Boolean = false,
    val error        : String? = null,
    val loggedIn     : Boolean = false    // true → navigate to AdminDashboard
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _otpState = MutableStateFlow(OtpUiState())
    val otpState: StateFlow<OtpUiState> = _otpState.asStateFlow()

    private val _adminLoginState = MutableStateFlow(AdminLoginUiState())
    val adminLoginState: StateFlow<AdminLoginUiState> = _adminLoginState.asStateFlow()

    fun requestOtp(pno: String) {
        if (pno.isBlank()) {
            _loginState.value = LoginUiState(error = "PNO दर्ज करें")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginUiState(isLoading = true)
            when (val result = authRepository.requestOtp(pno)) {
                is Resource.Success -> _loginState.value = LoginUiState(otpSent = true)
                is Resource.Error   -> _loginState.value = LoginUiState(error = result.message)
                else -> {}
            }
        }
    }

    fun verifyOtp(pno: String, otp: String) {
        if (otp.length < 4) {
            _otpState.value = OtpUiState(error = "सही OTP दर्ज करें")
            return
        }
        viewModelScope.launch {
            _otpState.value = OtpUiState(isLoading = true)
            when (val result = authRepository.verifyOtp(pno, otp)) {
                is Resource.Success -> _otpState.value = OtpUiState(verified = true)
                is Resource.Error   -> _otpState.value = OtpUiState(error = result.message)
                else -> {}
            }
        }
    }

    /**
     * Admin Password Login — PNO + Password से direct login।
     * OTP की ज़रूरत नहीं।
     */
    fun adminLogin(pno: String, password: String) {
        if (pno.isBlank()) {
            _adminLoginState.value = AdminLoginUiState(error = "PNO दर्ज करें")
            return
        }
        if (password.isBlank()) {
            _adminLoginState.value = AdminLoginUiState(error = "पासवर्ड दर्ज करें")
            return
        }
        viewModelScope.launch {
            _adminLoginState.value = AdminLoginUiState(isLoading = true)
            when (val result = authRepository.adminPasswordLogin(pno, password)) {
                is Resource.Success -> _adminLoginState.value = AdminLoginUiState(loggedIn = true)
                is Resource.Error   -> _adminLoginState.value = AdminLoginUiState(error = result.message)
                else -> {}
            }
        }
    }
}

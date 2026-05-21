package com.kavach.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.repository.AuthRepository
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.kavach.app.data.local.SessionDataStore
import javax.inject.Inject

data class LoginUiState(
    val isLoading : Boolean = false,
    val error     : String? = null,
    val loggedIn  : Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun login(pno: String, password: String) {
        if (pno.isBlank()) {
            _loginState.value = LoginUiState(error = "PNO दर्ज करें")
            return
        }
        if (password.isBlank()) {
            _loginState.value = LoginUiState(error = "पासवर्ड दर्ज करें")
            return
        }
        
        viewModelScope.launch {
            _loginState.value = LoginUiState(isLoading = true)
            when (val result = authRepository.loginWithPassword(pno, password)) {
                is ApiResult.Success -> {
                    delay(150)
                    val savedToken = sessionDataStore.getTokenOnce()
                    if (savedToken.isNotBlank()) {
                        android.util.Log.d("AUTH_FLOW", "Login success & token persisted.")
                        _loginState.value = LoginUiState(loggedIn = true)
                    } else {
                        _loginState.value = LoginUiState(error = "सत्र सुरक्षित नहीं हो सका।")
                    }
                }
                is ApiResult.Error        -> _loginState.value = LoginUiState(error = result.message)
                is ApiResult.Unauthorized -> _loginState.value = LoginUiState(error = result.message)
                else -> {
                    _loginState.value = LoginUiState(error = "अनपेक्षित त्रुटि")
                }
            }
        }
    }
}

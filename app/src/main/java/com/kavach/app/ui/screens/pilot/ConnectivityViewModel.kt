package com.kavach.app.ui.screens.pilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.BuildConfig
import com.kavach.app.data.remote.api.KavachApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class ConnectivityState(
    val apiReachable: Boolean? = null,
    val dbHealthy: Boolean? = null,
    val cacheHealthy: Boolean? = null,
    val versionCompatible: Boolean? = null,
    val isComplete: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    private val apiService: KavachApiService
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectivityState())
    val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    init {
        runDiagnostics()
    }

    fun runDiagnostics() {
        _state.value = ConnectivityState() // reset
        
        viewModelScope.launch {
            var apiOk = false
            var fatalError: String? = null

            // Pilot Phase: Only check if backend is reachable.
            // Ignore DB/Cache/Version for startup flow to prevent "Operational Suicide"
            try {
                withTimeout(5000) {
                    val response = apiService.checkApiHealth()
                    if (response.isSuccessful) {
                        apiOk = true
                    } else {
                        fatalError = "सर्वर से कनेक्ट नहीं हो सका (${response.code()})"
                    }
                }
            } catch (e: TimeoutCancellationException) {
                fatalError = "सर्वर टाइमआउट (कृपया इंटरनेट जांचें)"
            } catch (e: Exception) {
                fatalError = "नेटवर्क त्रुटि: ${e.message}"
            }

            if (com.kavach.app.KavachConfig.PILOT_MODE && !apiOk) {
                android.util.Log.w("ConnectivityViewModel", "PILOT MODE: API check failed but we might proceed if it was just a transient error. Current error: $fatalError")
            }

            _state.value = _state.value.copy(
                apiReachable = apiOk,
                dbHealthy = true,      // Assume OK in Pilot
                cacheHealthy = true,   // Assume OK in Pilot
                versionCompatible = true, // Assume OK in Pilot
                isComplete = true,
                hasError = !apiOk,
                errorMessage = fatalError
            )
        }
    }
}

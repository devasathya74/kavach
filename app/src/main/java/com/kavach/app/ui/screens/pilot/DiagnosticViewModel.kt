package com.kavach.app.ui.screens.pilot

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.BuildConfig
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class DiagnosticState(
    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = Build.VERSION.RELEASE,
    val integrityState: String = "Loading...",
    val lastSyncTime: String = "Loading...",
    val apiBaseUrl: String = BuildConfig.BASE_URL,
    val correlationId: String = UUID.randomUUID().toString(),
    val tunnelReachable: String = "Checking..."
)

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val sessionDataStore: SessionDataStore,
    private val apiService: KavachApiService
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticState())
    val state: StateFlow<DiagnosticState> = _state.asStateFlow()

    init {
        loadDiagnostics()
    }

    private fun loadDiagnostics() {
        viewModelScope.launch {
            val integrity = sessionDataStore.integrityLevel.first()
            val attestedAt = sessionDataStore.lastAttestedAt.first()
            
            val formattedTime = if (attestedAt > 0L) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(attestedAt))
            } else {
                "Never"
            }

            _state.value = _state.value.copy(
                integrityState = if (integrity.isNotBlank()) integrity else "UNKNOWN",
                lastSyncTime = formattedTime
            )

            checkTunnel()
        }
    }

    private suspend fun checkTunnel() {
        val result = withTimeoutOrNull(3000) {
            try {
                val response = apiService.checkApiHealth()
                if (response.isSuccessful) "OK (${response.raw().receivedResponseAtMillis - response.raw().sentRequestAtMillis}ms)" else "ERROR (${response.code()})"
            } catch (e: Exception) {
                "FAILED"
            }
        } ?: "TIMEOUT"

        _state.value = _state.value.copy(tunnelReachable = result)
    }
}

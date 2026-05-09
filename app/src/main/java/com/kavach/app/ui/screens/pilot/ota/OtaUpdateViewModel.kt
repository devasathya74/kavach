package com.kavach.app.ui.screens.pilot.ota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.system.OtaUpdateDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OtaUpdateState(
    val latestUpdate: OtaUpdateDto? = null,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null,
    val installStatus: String? = null // PENDING, DOWNLOADING, VERIFYING, READY, FAILED
)

@HiltViewModel
class OtaUpdateViewModel @Inject constructor(
    private val api: KavachApiV2
) : ViewModel() {

    private val _state = MutableStateFlow(OtaUpdateState())
    val state = _state.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val update = api.getLatestUpdate()
                _state.update { it.copy(latestUpdate = update, isLoading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun startDownload() {
        viewModelScope.launch {
            val update = _state.value.latestUpdate ?: return@launch
            _state.update { it.copy(isDownloading = true, installStatus = "DOWNLOADING") }
            
            // Simulating progress for now — real impl would use DownloadManager or custom resumable flow
            for (i in 1..100) {
                kotlinx.coroutines.delay(50)
                _state.update { it.copy(downloadProgress = i / 100f) }
            }
            
            _state.update { it.copy(isDownloading = false, installStatus = "READY") }
        }
    }
}

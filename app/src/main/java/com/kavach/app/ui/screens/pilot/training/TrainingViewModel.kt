package com.kavach.app.ui.screens.pilot.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.training.TrainingModuleDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainingState(
    val modules: List<TrainingModuleDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val api: KavachApiV2
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingState())
    val state = _state.asStateFlow()

    init {
        loadModules()
    }

    fun loadModules(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val results = api.getTrainingModules().map { dto ->
                    TrainingModuleDto(
                        id          = dto.id,
                        title       = dto.title,
                        description = dto.description ?: "",
                        videoUrl    = dto.videoUrl,
                        durationSec = dto.durationSec,
                        isMandatory = false,
                        isCompleted = dto.acknowledged,
                        acknowledged = dto.acknowledged
                    )
                }
                _state.update { it.copy(modules = results, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    fun acknowledge(id: String) {
        viewModelScope.launch {
            try {
                api.acknowledgeTraining(id)
                loadModules(isRefresh = true)
            } catch (e: Exception) {
                // Log error
            }
        }
    }
}

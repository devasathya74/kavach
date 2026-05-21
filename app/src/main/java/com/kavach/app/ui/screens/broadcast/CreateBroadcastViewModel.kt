package com.kavach.app.ui.screens.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.personnel.OfficerDto
import com.kavach.app.data.repository.MissionRepository
import com.kavach.app.data.repository.UserManagementRepository
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kavach.app.utils.onSuccess
import com.kavach.app.utils.onFailure

data class CreateBroadcastUiState(
    val title: String = "",
    val content: String = "",
    val priority: String = "INFO",
    val imageUrl: String? = null,
    val selectedOfficerIds: Set<String> = emptySet(),
    val officers: List<OfficerDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class CreateBroadcastViewModel @Inject constructor(
    private val missionRepository: MissionRepository,
    private val userRepository: UserManagementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateBroadcastUiState())
    val uiState: StateFlow<CreateBroadcastUiState> = _uiState.asStateFlow()

    init {
        loadOfficers()
    }

    private fun loadOfficers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userRepository.getOfficers().onSuccess { officers ->
                _uiState.value = _uiState.value.copy(
                    officers = officers,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun onContentChange(content: String) {
        _uiState.value = _uiState.value.copy(content = content)
    }

    fun onPriorityChange(priority: String) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun onImageUrlChange(url: String?) {
        _uiState.value = _uiState.value.copy(imageUrl = url)
    }

    fun toggleOfficerSelection(officerId: String) {
        val current = _uiState.value.selectedOfficerIds.toMutableSet()
        if (current.contains(officerId)) {
            current.remove(officerId)
        } else {
            current.add(officerId)
        }
        _uiState.value = _uiState.value.copy(selectedOfficerIds = current)
    }

    fun sendBroadcast() {
        if (_uiState.value.title.isBlank() || _uiState.value.content.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Title and Content are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)
            val result = missionRepository.createBroadcast(
                title = _uiState.value.title,
                content = _uiState.value.content,
                priority = _uiState.value.priority,
                imageUrl = _uiState.value.imageUrl,
                targetedOfficerIds = if (_uiState.value.selectedOfficerIds.isEmpty()) null else _uiState.value.selectedOfficerIds.toList()
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSending = false, success = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSending = false, error = result.message)
                }
                else -> {}
            }
        }
    }
}

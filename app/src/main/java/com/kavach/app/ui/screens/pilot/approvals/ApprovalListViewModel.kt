package com.kavach.app.ui.screens.pilot.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.system.DraftChangeDto
import com.kavach.app.data.repository.UserManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kavach.app.utils.onSuccess
import com.kavach.app.utils.onFailure

data class ApprovalListState(
    val pendingChanges: List<DraftChangeDto> = emptyList(),
    val currentPno: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val processingId: String? = null
)

@HiltViewModel
class ApprovalListViewModel @Inject constructor(
    private val repository: UserManagementRepository,
    private val sessionDataStore: com.kavach.app.data.local.SessionDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalListState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionDataStore.pno.collect { pno ->
                _state.update { it.copy(currentPno = pno ?: "") }
            }
        }
        loadPendingChanges()
    }

    fun loadPendingChanges() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.getPendingChanges().onSuccess { changes ->
                _state.update { it.copy(pendingChanges = changes, isLoading = false, error = null) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun approveChange(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(processingId = id) }
            repository.approveChange(id).onSuccess {
                loadPendingChanges()
            }.onFailure { e ->
                val errorMsg = when(e.message) {
                    "403" -> "RATIFICATION_BLOCKED: You cannot approve your own request."
                    "409" -> "CONFLICT: The officer's profile has changed. This request is now invalid."
                    else -> e.message ?: "Approval failed"
                }
                _state.update { it.copy(error = errorMsg, processingId = null) }
            }
        }
    }

    fun rejectChange(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(processingId = id) }
            repository.rejectChange(id).onSuccess {
                loadPendingChanges()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, processingId = null) }
            }
        }
    }
}

package com.kavach.app.ui.screens.pilot.personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.v2.CreateUserRequest
import com.kavach.app.data.remote.dto.v2.UpdateUserRequest
import com.kavach.app.data.remote.dto.v2.ResetPasswordRequest
import com.kavach.app.data.repository.UserManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kavach.app.utils.onSuccess
import com.kavach.app.utils.onFailure

sealed interface PersonnelUiState {
    object Idle : PersonnelUiState
    object Loading : PersonnelUiState
    data class Success(val users: List<PersonnelListItemUiModel>) : PersonnelUiState
    data class Error(val message: String) : PersonnelUiState
}

sealed interface ActionState {
    object Idle : ActionState
    object Processing : ActionState
    data class Success(val message: String) : ActionState
    data class Error(val message: String) : ActionState
}

data class PersonnelQueryState(
    val query: String = "",
    val unitType: String? = null,
    val company: String? = null,
    val platoon: Int? = null,
    val page: Int = 1
)

data class PersonnelListState(
    val uiState: PersonnelUiState = PersonnelUiState.Idle,
    val actionState: ActionState = ActionState.Idle,
    val isRefreshing: Boolean = false,
    val query: PersonnelQueryState = PersonnelQueryState(),
    val endOfPaginationReached: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val bulkMutationQueue: List<com.kavach.app.data.local.entity.BulkMutationEntity> = emptyList()
)

@HiltViewModel
class PersonnelListViewModel @Inject constructor(
    private val repository: UserManagementRepository,
    private val officerDao: com.kavach.app.data.local.dao.OfficerDao
) : ViewModel() {

    private val _state = MutableStateFlow(PersonnelListState())
    val state = _state.asStateFlow()

    private val pendingDeletions = MutableStateFlow<Set<String>>(emptySet())
    private var searchJob: Job? = null

    fun toggleSelectionMode(enabled: Boolean) {
        _state.update { it.copy(selectionMode = enabled, selectedIds = emptySet()) }
    }

    fun toggleUserSelection(id: String) {
        _state.update { 
            val newSelected = if (it.selectedIds.contains(id)) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = newSelected)
        }
    }

    fun bulkAction(actionType: String) {
        val selected = _state.value.selectedIds
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(actionState = ActionState.Processing) }
            repository.performBulkAction(selected.toList(), actionType)
                .onSuccess {
                    _state.update { 
                        it.copy(
                            actionState = ActionState.Success("Bulk action $actionType initiated for ${selected.size} users"),
                            selectedIds = emptySet(),
                            selectionMode = false
                        ) 
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(actionState = ActionState.Error(e.message ?: "Bulk action failed")) }
                }
        }
    }

    init {
        observeUsers()
        observeBulkQueue()
    }

    private fun observeBulkQueue() {
        officerDao.observeBulkMutations()
            .onEach { queue ->
                _state.update { it.copy(bulkMutationQueue = queue) }
            }.launchIn(viewModelScope)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeUsers() {
        val queryFlow = _state.map { it.query }.distinctUntilChanged()
        
        combine(
            queryFlow.flatMapLatest { q ->
                repository.observeUsers(q.unitType, q.query.ifBlank { null })
            },
            pendingDeletions
        ) { dtoList, pending ->
            val uiModels = dtoList
                .map { it.toUiModel() }
                .filter { it.id !in pending }
            
            if (uiModels.isEmpty() && _state.value.uiState is PersonnelUiState.Idle) {
                PersonnelUiState.Loading
            } else {
                PersonnelUiState.Success(uiModels)
            }
        }.onEach { newState ->
            _state.update { it.copy(uiState = newState) }
        }.launchIn(viewModelScope)
    }

    fun syncUsers(isRefresh: Boolean = false) {
        val currentState = _state.value
        if (currentState.isRefreshing) return
        if (!isRefresh && currentState.endOfPaginationReached) return

        val nextPage = if (isRefresh) 1 else currentState.query.page + 1

        viewModelScope.launch {
            if (isRefresh) {
                _state.update { it.copy(isRefreshing = true, endOfPaginationReached = false) }
            } else {
                // If we are already success, don't show full screen loading, just a footer loading (not implemented yet in UI)
                if (currentState.uiState !is PersonnelUiState.Success) {
                    _state.update { it.copy(uiState = PersonnelUiState.Loading) }
                }
            }

            repository.refreshUsers(
                page = nextPage,
                unitType = currentState.query.unitType,
                search = currentState.query.query.ifBlank { null }
            ).onSuccess { hasNext ->
                _state.update { 
                    it.copy(
                        query = it.query.copy(page = nextPage),
                        endOfPaginationReached = !hasNext,
                        isRefreshing = false
                    )
                }
            }.onFailure { e ->
                _state.update { 
                    it.copy(
                        uiState = PersonnelUiState.Error(e.message ?: "Failed to fetch users"),
                        isRefreshing = false
                    ) 
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(query = it.query.copy(query = query)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            syncUsers(isRefresh = true)
        }
    }

    fun onUnitFilterChange(unitType: String?) {
        _state.update { it.copy(query = it.query.copy(unitType = unitType, page = 1)) }
        syncUsers(isRefresh = true)
    }

    fun clearActionState() {
        _state.update { it.copy(actionState = ActionState.Idle) }
    }

    fun createUser(request: CreateUserRequest) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = ActionState.Processing) }
            repository.createUser(request)
                .onSuccess {
                    _state.update { it.copy(actionState = ActionState.Success("User created successfully")) }
                    syncUsers(isRefresh = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(actionState = ActionState.Error(e.message ?: "Creation failed")) }
                }
        }
    }

    fun updateUser(id: String, request: UpdateUserRequest) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = ActionState.Processing) }
            repository.updateUser(id, request)
                .onSuccess {
                    _state.update { it.copy(actionState = ActionState.Success("User updated successfully")) }
                }
                .onFailure { e ->
                    _state.update { it.copy(actionState = ActionState.Error(e.message ?: "Update failed")) }
                }
        }
    }

    fun deleteUser(id: String) {
        viewModelScope.launch {
            // Optimistic update
            pendingDeletions.update { it + id }
            _state.update { it.copy(actionState = ActionState.Processing) }

            repository.deleteUser(id)
                .onSuccess {
                    _state.update { it.copy(actionState = ActionState.Success("User deactivated")) }
                    pendingDeletions.update { it - id } // Confirmation: DB will handle it now
                }
                .onFailure { e ->
                    // Rollback
                    pendingDeletions.update { it - id }
                    _state.update { it.copy(actionState = ActionState.Error(e.message ?: "Deactivation failed")) }
                }
        }
    }

    fun resetPassword(id: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionState = ActionState.Processing) }
            repository.resetPassword(id, ResetPasswordRequest(password))
                .onSuccess {
                    _state.update { it.copy(actionState = ActionState.Success("Password reset successfully")) }
                }
                .onFailure { e ->
                    _state.update { it.copy(actionState = ActionState.Error(e.message ?: "Reset failed")) }
                }
        }
    }
}

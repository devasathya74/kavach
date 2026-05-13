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

data class PersonnelQueryState(
    val query: String = "",
    val unitType: String? = null,
    val company: String? = null,
    val platoon: Int? = null,
    val page: Int = 1
)

data class PersonnelListState(
    val users: List<PersonnelListItemUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val query: PersonnelQueryState = PersonnelQueryState(),
    val endOfPaginationReached: Boolean = false
)

@HiltViewModel
class PersonnelListViewModel @Inject constructor(
    private val repository: UserManagementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PersonnelListState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeUsers()
        syncUsers(isRefresh = true)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeUsers() {
        _state.map { it.query }.distinctUntilChanged().flatMapLatest { q ->
            repository.observeUsers(q.unitType, q.query.ifBlank { null })
        }.onEach { dtoList ->
            val uiModels = dtoList.map { it.toUiModel() }
            _state.update { it.copy(users = uiModels) }
        }.launchIn(viewModelScope)
    }

    fun syncUsers(isRefresh: Boolean = false) {
        val currentState = _state.value
        if (currentState.isLoading || currentState.isRefreshing) return

        viewModelScope.launch {
            if (isRefresh) _state.update { it.copy(isRefreshing = true) }
            else _state.update { it.copy(isLoading = true) }

            val result = repository.refreshUsers(
                page = if (isRefresh) 1 else currentState.query.page + 1,
                unitType = currentState.query.unitType,
                search = currentState.query.query.ifBlank { null }
            )

            _state.update { it.copy(isLoading = false, isRefreshing = false) }
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

    fun onCompanyFilterChange(company: String?) {
        _state.update { it.copy(query = it.query.copy(company = company, page = 1)) }
        syncUsers(isRefresh = true)
    }

    fun onPlatoonFilterChange(platoon: Int?) {
        _state.update { it.copy(query = it.query.copy(platoon = platoon, page = 1)) }
        syncUsers(isRefresh = true)
    }

    fun onRefresh() {
        syncUsers(isRefresh = true)
    }

    fun createUser(request: CreateUserRequest) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.createUser(request)
                .onSuccess {
                    syncUsers(isRefresh = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun updateUser(id: String, request: UpdateUserRequest) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.updateUser(id, request)
                .onSuccess {
                    syncUsers(isRefresh = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun deleteUser(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.deleteUser(id)
                .onSuccess {
                    syncUsers(isRefresh = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun resetPassword(id: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.resetPassword(id, ResetPasswordRequest(password))
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}

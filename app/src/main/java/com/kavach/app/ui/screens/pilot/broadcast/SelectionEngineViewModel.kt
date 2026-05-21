package com.kavach.app.ui.screens.pilot.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.dao.OfficerDao
import com.kavach.app.data.local.entity.OfficerWithProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SelectionEngineViewModel @Inject constructor(
    private val officerDao: OfficerDao
) : ViewModel() {

    // Canonical Selection State (Survives filters/search)
    private val _selectedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedUserIds: StateFlow<Set<String>> = _selectedUserIds.asStateFlow()

    // Search & Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedUnitId = MutableStateFlow<String?>(null)
    val selectedUnitId = _selectedUnitId.asStateFlow()

    private val _selectedCompanyId = MutableStateFlow<String?>(null)
    val selectedCompanyId = _selectedCompanyId.asStateFlow()

    // visible personnel loaded from DB
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val visiblePersonnel: StateFlow<List<OfficerWithProfile>> = combine(
        _searchQuery.debounce(500L),
        _selectedUnitId,
        _selectedCompanyId
    ) { query, unitId, companyId ->
        FilterState(query, unitId, companyId)
    }.flatMapLatest { state ->
        when {
            state.query.isNotBlank() -> {
                officerDao.searchPersonnel(state.query)
            }
            state.companyId != null -> {
                officerDao.observePersonnelByCompany(state.companyId)
            }
            state.unitId != null -> {
                officerDao.observePersonnelByUnit(state.unitId)
            }
            else -> {
                // If no filters applied, maybe show none or all based on policy.
                // For now, we return empty flow to force user to search or filter.
                flowOf(emptyList())
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        // When searching, clear hierarchical filters to avoid confusion, or keep them?
        // Usually, search is global or within filter. For now, global.
        if (query.isNotBlank()) {
            _selectedUnitId.value = null
            _selectedCompanyId.value = null
        }
    }

    fun onUnitSelected(unitId: String?) {
        _selectedUnitId.value = unitId
        _selectedCompanyId.value = null // reset children
        _searchQuery.value = ""
    }

    fun onCompanySelected(companyId: String?) {
        _selectedCompanyId.value = companyId
        _searchQuery.value = ""
    }

    fun toggleSelection(userId: String) {
        val current = _selectedUserIds.value.toMutableSet()
        if (current.contains(userId)) {
            current.remove(userId)
        } else {
            current.add(userId)
        }
        _selectedUserIds.value = current
    }

    fun clearSelection() {
        _selectedUserIds.value = emptySet()
    }
    
    fun setSelection(userIds: Set<String>) {
        _selectedUserIds.value = userIds
    }

    private data class FilterState(
        val query: String,
        val unitId: String?,
        val companyId: String?
    )
}

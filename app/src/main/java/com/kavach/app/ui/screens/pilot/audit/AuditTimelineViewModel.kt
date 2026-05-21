package com.kavach.app.ui.screens.pilot.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.OfficerActivityDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditTimelineState(
    val activities: List<OfficerActivityDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val endOfPaginationReached: Boolean = false,
    val searchQuery: String = "",
    val severityFilter: String? = null
)

@HiltViewModel
class AuditTimelineViewModel @Inject constructor(
    private val api: KavachApiV2
) : ViewModel() {

    private val _state = MutableStateFlow(AuditTimelineState())
    val state = _state.asStateFlow()

    init {
        loadTimeline()
    }

    fun loadTimeline(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.update { it.copy(isRefreshing = true, page = 1, endOfPaginationReached = false) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val response = api.getGlobalAuditTimeline(
                    page = _state.value.page,
                    severity = _state.value.severityFilter,
                    search = _state.value.searchQuery.ifBlank { null }
                )
                
                _state.update { current ->
                    current.copy(
                        activities = if (isRefresh) response.results else current.activities + response.results,
                        isLoading = false,
                        isRefreshing = false,
                        page = current.page + 1,
                        endOfPaginationReached = response.next == null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadTimeline(isRefresh = true)
    }

    fun onSeverityFilterChange(severity: String?) {
        _state.update { it.copy(severityFilter = severity) }
        loadTimeline(isRefresh = true)
    }
}

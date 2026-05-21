package com.kavach.app.ui.screens.pilot.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.FieldDataDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FieldDataState(
    val documents: List<FieldDataDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FieldDataViewModel @Inject constructor(
    private val api: KavachApiV2
) : ViewModel() {

    private val _state = MutableStateFlow(FieldDataState())
    val state = _state.asStateFlow()

    init {
        loadDocuments()
    }

    fun loadDocuments(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val results = api.getFieldData()
                _state.update { it.copy(documents = results, isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }
}

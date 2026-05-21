package com.kavach.app.ui.screens.user.broadcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.entity.BroadcastEntity
import com.kavach.app.data.repository.BroadcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BroadcastViewTab { ALL, EMERGENCY, UNREAD }

data class MyBroadcastsState(
    val broadcasts: List<BroadcastEntity> = emptyList(),
    val selectedTab: BroadcastViewTab = BroadcastViewTab.ALL,
    val isRefreshing: Boolean = false,
    val actionDone: String? = null
) {
    val all: List<BroadcastEntity>
        get() = broadcasts.sortedByDescending { it.createdAt }

    val unread: List<BroadcastEntity>
        get() = broadcasts.filter {
            it.status != "ACKNOWLEDGED" && it.status != "READ"
        }.sortedByDescending { it.createdAt }

    val emergency: List<BroadcastEntity>
        get() = broadcasts.filter {
            it.priority == "HIGH" || it.priority == "CRITICAL" || it.type == "EMERGENCY"
        }.sortedByDescending { it.createdAt }

    val displayList: List<BroadcastEntity>
        get() = when (selectedTab) {
            BroadcastViewTab.ALL -> all
            BroadcastViewTab.UNREAD -> unread
            BroadcastViewTab.EMERGENCY -> emergency
        }
}

/**
 * MyBroadcastsViewModel — Received broadcasts only.
 *
 * USER RULE: Only shows broadcasts in the local DB.
 * Server has already filtered which broadcasts this user receives.
 * Never accesses delivery analytics, recipient lists, or dispatch data.
 */
@HiltViewModel
class MyBroadcastsViewModel @Inject constructor(
    private val broadcastDao: BroadcastDao,
    private val broadcastRepository: BroadcastRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MyBroadcastsState())
    val state: StateFlow<MyBroadcastsState> = _state.asStateFlow()

    init {
        observeBroadcasts()
        refresh()
    }

    private fun observeBroadcasts() {
        viewModelScope.launch {
            broadcastDao.observeAllBroadcasts()
                .catch { }
                .collect { list ->
                    _state.update { it.copy(broadcasts = list) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try { broadcastRepository.refreshBroadcasts() } catch (e: Exception) { }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectTab(tab: BroadcastViewTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun markAsRead(broadcastId: String) {
        viewModelScope.launch {
            broadcastRepository.markAsRead(broadcastId)
        }
    }

    fun acknowledge(broadcastId: String) {
        viewModelScope.launch {
            broadcastRepository.acknowledgeBroadcast(broadcastId)
            _state.update { it.copy(actionDone = "Broadcast Acknowledged") }
        }
    }

    fun clearActionDone() {
        _state.update { it.copy(actionDone = null) }
    }
}

package com.kavach.app.ui.screens.user.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.dao.SosDao
import com.kavach.app.data.local.dao.UserIncidentDao
import com.kavach.app.data.remote.websocket.WebSocketManager
import com.kavach.app.data.remote.websocket.WsEvent
import com.kavach.app.util.ConnectionStatus
import com.kavach.app.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncStatusState(
    val isOnline: Boolean = true,
    val wsConnected: Boolean = false,
    val wsStateName: String = "DISCONNECTED",
    val pendingSosCount: Int = 0,
    val pendingIncidentsCount: Int = 0,
    val pendingAttachmentsCount: Int = 0
) {
    val totalPendingUploads: Int
        get() = pendingSosCount + pendingIncidentsCount + pendingAttachmentsCount

    val isSyncing: Boolean
        get() = totalPendingUploads > 0 && isOnline
}

@HiltViewModel
class MySyncStatusViewModel @Inject constructor(
    private val sosDao: SosDao,
    private val userIncidentDao: UserIncidentDao,
    private val wsManager: WebSocketManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val wsState: StateFlow<Pair<Boolean, String>> = wsManager.events
        .filterIsInstance<WsEvent.StateChanged>()
        .map { 
            val connected = it.state == WebSocketManager.ConnectionState.CONNECTED
            val stateName = it.state.name
            Pair(connected, stateName)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(false, "DISCONNECTED"))

    val state: StateFlow<SyncStatusState> = combine(
        networkMonitor.status,
        wsState,
        sosDao.observePendingCount(),
        userIncidentDao.observePendingUploadCount(),
        userIncidentDao.observePendingAttachmentCount()
    ) { connStatus, wsInfo, pendingSos, pendingIncidents, pendingAttachments ->
        SyncStatusState(
            isOnline = connStatus == ConnectionStatus.AVAILABLE,
            wsConnected = wsInfo.first,
            wsStateName = wsInfo.second,
            pendingSosCount = pendingSos,
            pendingIncidentsCount = pendingIncidents,
            pendingAttachmentsCount = pendingAttachments
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncStatusState()
    )
}

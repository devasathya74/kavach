package com.kavach.app.core.reconciliation

import com.kavach.app.data.remote.websocket.WebSocketManager
import com.kavach.app.data.remote.websocket.WsEvent
import com.kavach.app.data.repository.IncidentRepository
import com.kavach.app.data.repository.UserManagementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OperationalSyncController — Central dispatcher for real-time operational events.
 * Listens to WebSocket events and triggers appropriate reconciliation in repositories.
 * 
 * Ensures the "Observer-Only UI" pattern:
 * WS -> Controller -> Repository -> DB -> Flow -> UI
 */
@Singleton
class OperationalSyncController @Inject constructor(
    private val wsManager: WebSocketManager,
    private val incidentRepository: IncidentRepository,
    private val orderRepository: com.kavach.app.data.repository.OrderRepository,
    private val broadcastRepository: com.kavach.app.data.repository.BroadcastRepository,
    private val userRepository: UserManagementRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        observeEvents()
    }

    private fun observeEvents() {
        wsManager.events
            .onEach { event ->
                when (event) {
                    is WsEvent.NewIncident -> {
                        Timber.d("SyncController: New incident event received for ${event.incidentId}")
                        incidentRepository.reconcileFromServer(event.incidentId)
                    }
                    is WsEvent.NewOrder -> {
                        Timber.d("SyncController: New order event received for ${event.orderId}")
                        orderRepository.reconcileFromServer(event.orderId)
                    }
                    is WsEvent.NewBroadcast -> {
                        Timber.d("SyncController: New broadcast event received for ${event.broadcastId}")
                        broadcastRepository.reconcileFromServer(event.broadcastId)
                    }
                    is WsEvent.StateChanged -> {
                        Timber.d("SyncController: WebSocket state changed to ${event.state}")
                        if (event.state == WebSocketManager.ConnectionState.CONNECTED) {
                            // On reconnect, trigger a light reconciliation of active modules
                            incidentRepository.observeIncidents() // Just to ensure observation? No, need a refresh.
                        }
                    }
                    else -> {}
                }
            }
            .launchIn(scope)
    }
}

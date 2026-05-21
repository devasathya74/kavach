package com.kavach.app.ui.screens.pilot.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.websocket.WebSocketManager
import com.kavach.app.data.remote.websocket.WsEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class LiveSessionState(
    val isLive: Boolean = false,
    val isConnected: Boolean = false,
    val isLowNetwork: Boolean = false,
    val qnaState: String = "WAITING", // WAITING -> APPROVED -> LIVE -> ENDED
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val currentStreamUrl: String? = null,
    val hasRaisedHand: Boolean = false,
    val isMicEnabled: Boolean = false,
    val newOrder: OrderPopup? = null,
    val error: String? = null
)

data class OrderPopup(
    val orderId: String,
    val content: String
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val wsManager: WebSocketManager,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveSessionState())
    val uiState: StateFlow<LiveSessionState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val token = sessionDataStore.token.firstOrNull()
            if (token != null) {
                wsManager.connect(token)
            }
        }

        viewModelScope.launch {
            wsManager.events.collect { event ->
                when (event) {
                    is WsEvent.StateChanged -> {
                        val connected = event.state == WebSocketManager.ConnectionState.CONNECTED
                        _uiState.value = _uiState.value.copy(isConnected = connected)
                    }
                    is WsEvent.LiveStart -> {
                        val video = event.streamUrl
                        val audio = video?.replace("stream.m3u8", "audio.m3u8")
                        _uiState.value = _uiState.value.copy(
                            isLive = true,
                            videoUrl = video,
                            audioUrl = audio,
                            currentStreamUrl = if (_uiState.value.isLowNetwork) audio else video
                        )
                    }
                    is WsEvent.LiveEnd -> {
                        _uiState.value = _uiState.value.copy(
                            isLive = false,
                            videoUrl = null,
                            audioUrl = null,
                            currentStreamUrl = null,
                            isMicEnabled = false
                        )
                    }
                    is WsEvent.QnaApproved -> {
                        _uiState.value = _uiState.value.copy(
                            qnaState = "APPROVED",
                            isMicEnabled = true
                        )
                    }
                    is WsEvent.MuteAll -> {
                        _uiState.value = _uiState.value.copy(
                            isMicEnabled = false,
                            qnaState = "WAITING"
                        )
                    }
                    is WsEvent.NewOrder -> {
                        _uiState.value = _uiState.value.copy(
                            newOrder = OrderPopup(event.orderId, event.content)
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    fun onNetworkStatusChanged(isLow: Boolean) {
        if (_uiState.value.isLowNetwork == isLow) return
        _uiState.value = _uiState.value.copy(isLowNetwork = isLow)
        // Adaptive bitrate switch
        if (_uiState.value.isLive) {
            val newUrl = if (isLow) _uiState.value.audioUrl else _uiState.value.videoUrl
            _uiState.value = _uiState.value.copy(currentStreamUrl = newUrl)
        }
    }

    fun onStreamError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun raiseHand() {
        _uiState.value = _uiState.value.copy(hasRaisedHand = true)
        val json = JSONObject().apply { put("type", "RAISE_HAND") }
        wsManager.send(json.toString())
    }

    fun acknowledgeOrder() {
        val order = _uiState.value.newOrder ?: return
        val json = JSONObject().apply {
            put("type", "ACK_ORDER")
            put("orderId", order.orderId)
        }
        wsManager.send(json.toString())
        _uiState.value = _uiState.value.copy(newOrder = null)
    }

    fun sendAnonymousReport(message: String) {
        val json = JSONObject().apply {
            put("type", "ANONYMOUS_REPORT")
            put("message", message)
            put("timestamp", System.currentTimeMillis())
        }
        wsManager.send(json.toString())
    }

    override fun onCleared() {
        super.onCleared()
        wsManager.disconnect()
    }
}

package com.kavach.app.data.remote.websocket

import android.util.Log
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val moshi: Moshi,
    private val eventBus: EventBus
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTH_FAILED,
        RECONNECT_WAIT
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private var currentToken: String? = null
    private var isIntentionallyDisconnected = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var lastState: ConnectionState = ConnectionState.DISCONNECTED

    // ── High-Fidelity Session Tracking ────────────────────────
    private var currentSessionId: String = "IDLE"
    private var socketGeneration: Int = 0

    companion object {
        private const val MAX_DEDUPE_CACHE = 128
        private const val TAG_WS = "KAVACH_WS"
    }

    fun connect(token: String) {
        if (currentToken == token && lastState == ConnectionState.CONNECTED) {
            Log.d(TAG_WS, "[$currentSessionId] Already connected. Skipping.")
            return
        }
        
        currentSessionId = "SES_${System.currentTimeMillis() % 10000}"
        socketGeneration = 0
        Log.i(TAG_WS, "[$currentSessionId] Initiating explicit connection sequence...")
        
        forceCleanup("USER_CONNECT")
        
        currentToken = token
        isIntentionallyDisconnected = false
        reconnectAttempt = 0
        
        internalConnect("INITIAL_HANDSHAKE")
    }

    private val processedEventIds = LinkedHashSet<String>()

    private fun internalConnect(reason: String) {
        if (webSocket != null) return
        val token = currentToken ?: return

        socketGeneration++
        updateState(ConnectionState.CONNECTING, reason)

        val wsBase = com.kavach.app.KavachConfig.BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val wsUrl = "${wsBase}ws/live/?token=$token"
        
        Log.i(TAG_WS, "[$currentSessionId] Gen:$socketGeneration | Attempting connection ($reason): $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Kavach-Client", "Android-Pilot-v2")
            .addHeader("X-Kavach-Session", currentSessionId)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG_WS, "[$currentSessionId] Gen:$socketGeneration | CONNECTED ✓")
                reconnectAttempt = 0
                updateState(ConnectionState.CONNECTED, "SERVER_OPEN")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    val eventId = envelope.optString("event_id")
                    
                    if (eventId.isNotEmpty()) {
                        synchronized(processedEventIds) {
                            if (processedEventIds.contains(eventId)) {
                                Log.d(TAG_WS, "[$currentSessionId] DEDUPE: $eventId. Skipping.")
                                return
                            }
                            processedEventIds.add(eventId)
                            if (processedEventIds.size > MAX_DEDUPE_CACHE) {
                                processedEventIds.remove(processedEventIds.iterator().next())
                            }
                        }
                    }

                    val type = envelope.optString("type")
                    val payload = envelope.optJSONObject("payload") ?: envelope
                    
                    Log.d(TAG_WS, "[$currentSessionId] EVENT: $type | ID: ${eventId.take(8)}")

                    when (type) {
                        "LIVE_START" -> emitEvent(WsEvent.LiveStart(payload.getString("streamUrl")))
                        "LIVE_END" -> emitEvent(WsEvent.LiveEnd)
                        "QNA_APPROVED" -> emitEvent(WsEvent.QnaApproved(payload.getString("sessionId")))
                        "NEW_ORDER" -> emitEvent(WsEvent.NewOrder(payload.getString("orderId"), payload.getString("content")))
                        "NEW_INCIDENT" -> emitEvent(WsEvent.NewIncident(payload.getString("incidentId"), payload.optString("severity")))
                        "NEW_BROADCAST" -> emitEvent(WsEvent.NewBroadcast(payload.getString("broadcastId")))
                        "PING" -> webSocket.send("{\"type\":\"PONG\"}")
                        "INFO" -> Log.i(TAG_WS, "[$currentSessionId] SYSTEM: ${payload.optString("message")}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG_WS, "[$currentSessionId] Parsing Error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG_WS, "[$currentSessionId] CLOSING: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG_WS, "[$currentSessionId] CLOSED")
                updateState(ConnectionState.DISCONNECTED, "SERVER_CLOSED")
                this@WebSocketManager.webSocket = null
                if (!isIntentionallyDisconnected) handleReconnect("CLOSED_BY_SERVER")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                Log.e(TAG_WS, "[$currentSessionId] FAILURE: Code=$code | Error=${t.message}")
                
                this@WebSocketManager.webSocket = null
                
                if (code == 403 || code == 401) {
                    updateState(ConnectionState.AUTH_FAILED, "TOKEN_EXPIRED")
                    return 
                }
                
                updateState(ConnectionState.DISCONNECTED, "NETWORK_FAILURE")
                handleReconnect("FAILURE_RETRY")
            }
        })
    }

    private fun handleReconnect(reason: String) {
        if (isIntentionallyDisconnected) return
        
        reconnectJob?.cancel()
        updateState(ConnectionState.RECONNECT_WAIT, reason)
        
        reconnectJob = scope.launch {
            val baseDelay = (2000L * (1 shl reconnectAttempt)).coerceAtMost(60000L)
            val jitter = (0..2000).random().toLong()
            val totalDelay = baseDelay + jitter
            
            Log.d(TAG_WS, "[$currentSessionId] Reconnect attempt $reconnectAttempt in ${totalDelay}ms (Reason: $reason)")
            delay(totalDelay)
            reconnectAttempt++
            internalConnect(reason)
        }
    }

    private fun forceCleanup(reason: String) {
        Log.w(TAG_WS, "[$currentSessionId] FORCE_CLEANUP: $reason")
        reconnectJob?.cancel()
        webSocket?.close(1001, "Cleanup: $reason")
        webSocket = null
    }

    fun disconnect() {
        isIntentionallyDisconnected = true
        forceCleanup("USER_DISCONNECT")
        currentToken = null
        updateState(ConnectionState.DISCONNECTED, "USER_LOGOUT")
    }

    fun send(message: String) {
        if (lastState == ConnectionState.CONNECTED) {
            webSocket?.send(message)
        } else {
            Log.e(TAG_WS, "[$currentSessionId] Cannot send: State is $lastState")
        }
    }

    fun isConnected(): Boolean = lastState == ConnectionState.CONNECTED

    /**
     * sendSosSignal — Priority SOS payload via WebSocket.
     * Sends a structured JSON message to the server's WS handler.
     */
    fun sendSosSignal(
        correlationId: String,
        senderPno: String,
        senderUnit: String,
        message: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val payload = buildString {
            append("{")
            append("\"type\":\"SOS\",")
            append("\"correlation_id\":\"$correlationId\",")
            append("\"sender_pno\":\"$senderPno\",")
            append("\"sender_unit\":\"$senderUnit\",")
            append("\"message\":\"$message\"")
            if (latitude != null) append(",\"latitude\":$latitude")
            if (longitude != null) append(",\"longitude\":$longitude")
            append("}")
        }
        send(payload)
        Log.i(TAG_WS, "[$currentSessionId] SOS_SIGNAL sent: correlationId=$correlationId")
    }

    private fun updateState(state: ConnectionState, reason: String) {
        lastState = state
        emitEvent(WsEvent.StateChanged(state))
        Log.i(TAG_WS, "[$currentSessionId] STATE_CHANGE: ${state.name} | Reason: $reason")
        
        when (state) {
            ConnectionState.CONNECTED -> eventBus.emit(SystemEvent.WebSocketConnected)
            ConnectionState.RECONNECT_WAIT -> eventBus.emit(SystemEvent.WebSocketReconnecting(reconnectAttempt))
            ConnectionState.AUTH_FAILED -> eventBus.emit(SystemEvent.SessionExpired("WEBSOCKET_AUTH_FAILURE"))
            else -> {}
        }
    }

    private fun emitEvent(event: WsEvent) {
        _events.tryEmit(event)
    }
}

sealed class WsEvent {
    data class StateChanged(val state: WebSocketManager.ConnectionState) : WsEvent()
    data class LiveStart(val streamUrl: String) : WsEvent()
    object LiveEnd : WsEvent()
    data class QnaApproved(val sessionId: String) : WsEvent()
    object MuteAll : WsEvent()
    data class NewOrder(val orderId: String, val content: String) : WsEvent()
    data class NewIncident(val incidentId: String, val severity: String) : WsEvent()
    data class NewBroadcast(val broadcastId: String) : WsEvent()
}

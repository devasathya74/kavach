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

    // ── WebSocket Auth Refresh Hardening ─────────────────────
    
    fun connect(token: String) {
        if (currentToken == token && lastState == ConnectionState.CONNECTED) {
            Log.d("WebSocket", "Already connected with this token. Skipping.")
            return
        }
        
        Log.d("WebSocket", "Initiating explicit connection sequence...")
        
        // CRITICAL: Force clear existing state to prevent duplicates
        forceCleanup()
        
        currentToken = token
        isIntentionallyDisconnected = false
        reconnectAttempt = 0
        
        internalConnect()
    }

    private val processedEventIds = LinkedHashSet<String>()

    companion object {
        private const val MAX_DEDUPE_CACHE = 128
    }

    private fun internalConnect() {
        if (webSocket != null) return
        val token = currentToken ?: return

        updateState(ConnectionState.CONNECTING)

        val wsBase = com.kavach.app.KavachConfig.BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val wsUrl = "${wsBase}ws/live/?token=$token"
        
        Log.i("WebSocket", "Attempting connection: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Kavach-Client", "Android-Pilot-v2")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("WebSocket", "CONNECTED ✓")
                reconnectAttempt = 0
                updateState(ConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    val eventId = envelope.optString("event_id")
                    
                    // 1. DEDUPLICATION: Prevent re-processing on reconnect replays
                    if (eventId.isNotEmpty()) {
                        synchronized(processedEventIds) {
                            if (processedEventIds.contains(eventId)) {
                                Log.d("WebSocket", "DEDUPE: Event $eventId already processed. Skipping.")
                                return
                            }
                            processedEventIds.add(eventId)
                            if (processedEventIds.size > MAX_DEDUPE_CACHE) {
                                val first = processedEventIds.iterator().next()
                                processedEventIds.remove(first)
                            }
                        }
                    }

                    // 2. CONTRACT PARSING: Extract type and payload
                    val type = envelope.optString("type")
                    val payload = envelope.optJSONObject("payload") ?: envelope // Backward compatibility
                    
                    Log.d("WebSocket", "EVENT: $type | ID: ${eventId.take(8)}...")

                    when (type) {
                        "LIVE_START" -> emitEvent(WsEvent.LiveStart(payload.getString("streamUrl")))
                        "LIVE_END" -> emitEvent(WsEvent.LiveEnd)
                        "QNA_APPROVED" -> emitEvent(WsEvent.QnaApproved(payload.getString("sessionId")))
                        "NEW_ORDER" -> emitEvent(WsEvent.NewOrder(payload.getString("orderId"), payload.getString("content")))
                        "PING" -> webSocket.send("{\"type\":\"PONG\"}")
                        "INFO" -> Log.i("WebSocket", "System Message: ${payload.optString("message")}")
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Parsing Error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("WebSocket", "CLOSING: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "CLOSED")
                updateState(ConnectionState.DISCONNECTED)
                this@WebSocketManager.webSocket = null
                if (!isIntentionallyDisconnected) handleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                Log.e("WebSocket", "FAILURE: Code=$code | Error=${t.message}")
                
                this@WebSocketManager.webSocket = null
                
                if (code == 403 || code == 401) {
                    updateState(ConnectionState.AUTH_FAILED)
                    Log.e("WebSocket", "Auth failed. Stopping reconnect.")
                    return 
                }
                
                updateState(ConnectionState.DISCONNECTED)
                handleReconnect()
            }
        })
    }

    private fun handleReconnect() {
        if (isIntentionallyDisconnected) return
        
        reconnectJob?.cancel()
        updateState(ConnectionState.RECONNECT_WAIT)
        
        reconnectJob = scope.launch {
            val baseDelay = (2000L * (1 shl reconnectAttempt)).coerceAtMost(60000L)
            val jitter = (0..2000).random().toLong()
            val totalDelay = baseDelay + jitter
            
            Log.d("WebSocket", "Retrying in ${totalDelay}ms...")
            delay(totalDelay)
            reconnectAttempt++
            internalConnect()
        }
    }

    private fun forceCleanup() {
        reconnectJob?.cancel()
        webSocket?.close(1001, "Reconnecting/Cleanup")
        webSocket = null
    }

    fun disconnect() {
        isIntentionallyDisconnected = true
        forceCleanup()
        currentToken = null
        updateState(ConnectionState.DISCONNECTED)
    }

    fun send(message: String) {
        if (lastState == ConnectionState.CONNECTED) {
            webSocket?.send(message)
        } else {
            Log.e("WebSocket", "Cannot send: State is $lastState")
        }
    }

    private fun updateState(state: ConnectionState) {
        lastState = state
        emitEvent(WsEvent.StateChanged(state))
        
        // Bridge to global SystemEvent for UI reactivity
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
}

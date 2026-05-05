package com.kavach.app.data.remote.websocket

import android.util.Log
import com.squareup.moshi.Moshi
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
    private val moshi: Moshi
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    fun connect(token: String) {
        if (webSocket != null) return

        // In production, BASE_URL should be dynamically fetched from config and convert http->ws
        val wsUrl = "ws://10.0.2.2:8000/ws/live/?token=$token" 
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
                emitEvent(WsEvent.ConnectionState(true))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Message: $text")
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "LIVE_START" -> emitEvent(WsEvent.LiveStart(json.getString("streamUrl")))
                        "LIVE_END" -> emitEvent(WsEvent.LiveEnd)
                        "QNA_APPROVED" -> emitEvent(WsEvent.QnaApproved(json.getString("sessionId")))
                        "NEW_ORDER" -> {
                            val orderId = json.getString("orderId")
                            val content = json.getString("content")
                            emitEvent(WsEvent.NewOrder(orderId, content))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error parsing message", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closed")
                emitEvent(WsEvent.ConnectionState(false))
                this@WebSocketManager.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Failure", t)
                emitEvent(WsEvent.ConnectionState(false))
                this@WebSocketManager.webSocket = null
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun send(message: String) {
        webSocket?.send(message)
    }

    private fun emitEvent(event: WsEvent) {
        _events.tryEmit(event)
    }
}

sealed class WsEvent {
    data class ConnectionState(val isConnected: Boolean) : WsEvent()
    data class LiveStart(val streamUrl: String) : WsEvent()
    object LiveEnd : WsEvent()
    data class QnaApproved(val sessionId: String) : WsEvent()
    object MuteAll : WsEvent()
    data class NewOrder(val orderId: String, val content: String) : WsEvent()
}

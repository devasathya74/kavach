package com.kavach.app.core.telemetry

/**
 * NetworkTelemetry — Immutable snapshot of current network operational metrics.
 *
 * Emitted by TelemetryManager every [intervalMs] milliseconds.
 * Screens observe these values to render the telemetry bar.
 */
data class NetworkTelemetry(
    val apiRttMs          : Long    = 0L,       // API round-trip time
    val wsLatencyMs       : Long    = 0L,       // WebSocket round-trip
    val packetLossPct     : Float   = 0f,       // 0.0–100.0
    val reconnectAttempts : Int     = 0,
    val heartbeatIntervalMs : Long  = 5_000L,
    val syncQueueSize     : Int     = 0,
    val conferenceBitrateKbps : Int = 0,
    val encryptionMode    : String  = "AES-256",
    val uplinkStatus      : UplinkStatus = UplinkStatus.CONNECTED,
    val timestamp         : Long    = System.currentTimeMillis()
) {
    val isHealthy get() = uplinkStatus == UplinkStatus.CONNECTED && packetLossPct < 5f && apiRttMs < 800L
    val qualityLabel get() = when {
        packetLossPct > 20f || apiRttMs > 2000L -> "DEGRADED"
        packetLossPct > 8f  || apiRttMs > 800L  -> "UNSTABLE"
        else                                      -> "NOMINAL"
    }
}

enum class UplinkStatus { CONNECTED, RECONNECTING, DEGRADED, OFFLINE }

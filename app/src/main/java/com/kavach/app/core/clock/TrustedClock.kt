package com.kavach.app.core.clock

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TrustedClock — Distributed time authority.
 *
 * Maintains a synchronized offset between the device clock and the server clock.
 * All audit events, timestamps, and sequencing MUST use [nowMs] / [nowIso]
 * instead of System.currentTimeMillis() or Date() directly.
 *
 * Why this matters:
 *   - Device clocks can be manipulated (advanced or rewound)
 *   - Audit trail corruption becomes possible without trusted time
 *   - Replay attacks rely on timestamp ambiguity
 *
 * Usage:
 *   Inject TrustedClock, call [sync] on login/session restore,
 *   then use [nowMs] for all timestamps.
 *
 * If server sync fails → falls back to device time with [isSynced] = false.
 */
@Singleton
class TrustedClock @Inject constructor() {

    companion object {
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneId.of("UTC"))
    }

    /** True when at least one successful server sync has occurred. */
    val isSynced: StateFlow<Boolean> get() = _isSynced.asStateFlow()
    private val _isSynced = MutableStateFlow(false)

    /** The drift between device time and server time (ms). server_ms = device_ms + offset. */
    private var serverOffsetMs: Long = 0L

    /** Elapsed time snapshot taken at last sync, for drift-free tracking. */
    private var elapsedAtSync: Long = 0L
    private var serverMsAtSync: Long = 0L

    /**
     * Sync with server time. Call this after successful login.
     * [serverTimestampMs] is the epoch-ms returned by the API.
     */
    fun sync(serverTimestampMs: Long) {
        val deviceNow = System.currentTimeMillis()
        serverOffsetMs = serverTimestampMs - deviceNow
        elapsedAtSync  = SystemClock.elapsedRealtime()
        serverMsAtSync = serverTimestampMs
        _isSynced.value = true
    }

    /**
     * Returns the current trusted epoch time in milliseconds.
     * Uses elapsed-realtime to avoid system clock manipulation after sync.
     */
    fun nowMs(): Long {
        return if (_isSynced.value) {
            val elapsedSinceSync = SystemClock.elapsedRealtime() - elapsedAtSync
            serverMsAtSync + elapsedSinceSync
        } else {
            System.currentTimeMillis()
        }
    }

    /** Returns ISO-8601 UTC string suitable for audit records. */
    fun nowIso(): String = FORMATTER.format(Instant.ofEpochMilli(nowMs()))

    /** Returns the current server offset for diagnostic display. */
    fun offsetMs(): Long = serverOffsetMs

    /** Formats any epoch ms as ISO-8601 UTC string. */
    fun toIso(epochMs: Long): String = FORMATTER.format(Instant.ofEpochMilli(epochMs))

    /** Returns a short timestamp string for display (HH:mm:ss). */
    fun nowShort(): String = java.time.format.DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("UTC"))
        .format(Instant.ofEpochMilli(nowMs()))
}

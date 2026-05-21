package com.kavach.app.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionTimeoutManager — forces logout after idle inactivity.
 *
 * Security rationale:
 *  An officer leaves their phone unlocked and unattended.
 *  Another person can access sensitive orders/training status.
 *  Automatic logout after N minutes of inactivity prevents this.
 *
 * Usage:
 *  1. Call ping() on any user interaction (tap, scroll, navigation)
 *  2. Observe sessionExpiredEvent in MainActivity → navigate to Login
 *  3. Call start() when user logs in
 *  4. Call stop() when user explicitly logs out
 *
 * Config:
 *  TIMEOUT_MS = 10 minutes (configurable, adjust for field requirements)
 */
@Singleton
class SessionTimeoutManager @Inject constructor() {

    companion object {
        const val TIMEOUT_MS     = 10L * 60 * 1000   // 10 minutes
        const val WARNING_MS     = 9L  * 60 * 1000   // warn at 9 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    private val _warningEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val warningEvent: SharedFlow<Long> = _warningEvent.asSharedFlow()  // seconds remaining

    private var timeoutJob  : Job?  = null
    private var warningJob  : Job?  = null
    private var lastPingMs  : Long  = 0L
    private var isRunning   : Boolean = false

    /** Call on login to start the timer. */
    fun start() {
        isRunning   = true
        lastPingMs  = System.currentTimeMillis()
        resetTimer()
    }

    /** Call on logout to cancel all timers. */
    fun stop() {
        isRunning = false
        timeoutJob?.cancel()
        warningJob?.cancel()
    }

    /**
     * Call on any user interaction to reset the idle timer.
     * Lightweight — safe to call on every composable recomposition.
     */
    fun ping() {
        if (!isRunning) return
        lastPingMs = System.currentTimeMillis()
        resetTimer()
    }

    private fun resetTimer() {
        timeoutJob?.cancel()
        warningJob?.cancel()

        // Warning at 9 minutes
        warningJob = scope.launch {
            delay(WARNING_MS)
            if (isActive && isRunning) {
                val secondsLeft = (TIMEOUT_MS - WARNING_MS) / 1000
                _warningEvent.emit(secondsLeft)
            }
        }

        // Logout at 10 minutes
        timeoutJob = scope.launch {
            delay(TIMEOUT_MS)
            if (isActive && isRunning) {
                isRunning = false
                _sessionExpiredEvent.emit(Unit)
            }
        }
    }
}

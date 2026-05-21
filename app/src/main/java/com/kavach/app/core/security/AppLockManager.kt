package com.kavach.app.core.security

import com.kavach.app.data.local.SessionDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppLockManager — Orchestrates local application access control.
 *
 * Separates server authentication (JWT) from local device access (App Lock).
 * Handles timeouts, background relocking, and biometric/PIN transitions.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val sessionDataStore: SessionDataStore
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private val _lockState = MutableStateFlow<AppLockState>(AppLockState.Unlocked)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    init {
        scope.launch {
            sessionDataStore.isAppLocked.collect { isLocked ->
                if (isLocked) {
                    if (_lockState.value == AppLockState.Unlocked) {
                        _lockState.value = AppLockState.Locked
                    }
                } else {
                    _lockState.value = AppLockState.Unlocked
                }
            }
        }
    }

    /** Configurable timeouts based on security level. */
    var lockTimeoutMs: Long = 300_000L // Default 5 mins

    /** Point 2 FIX: Prevent lock loop if app backgrounded/foregrounded immediately after unlock */
    private val minimumUnlockWindowMs: Long = 10_000L // 10s

    /**
     * Call when the app enters the background.
     */
    fun onAppBackgrounded() {
        // No immediate lock, handled by foreground check + lifecycle delay in MainActivity
    }

    /**
     * Call when the app enters the foreground.
     * Evaluates if the app should be relocked based on timeout.
     */
    fun onAppForegrounded() {
        scope.launch {
            val lastUnlock = sessionDataStore.lastUnlockTime.first()
            val now = System.currentTimeMillis()
            
            // Point 2: If unlocked < 10s ago, ignore relock (prevents loop)
            if (now - lastUnlock < minimumUnlockWindowMs) {
                Timber.d("Ignoring relock: inside minimum unlock window.")
                return@launch
            }

            if (now - lastUnlock > lockTimeoutMs) {
                lock()
            }
        }
    }

    /** Manually lock the application. */
    fun lock() {
        scope.launch {
            val hasPin = sessionDataStore.isPinSet.first()
            if (hasPin) {
                _lockState.value = AppLockState.Locked
                sessionDataStore.lockApp()
            } else {
                Timber.d("AppLock: Ignoring lock request because no PIN is set.")
            }
        }
    }

    /** Mark as unlocked (after successful Biometric/PIN). */
    fun unlock() {
        _lockState.value = AppLockState.Unlocked
        scope.launch { sessionDataStore.saveUnlockTime(System.currentTimeMillis()) }
    }

    /** Transition to PIN requirement. */
    fun requirePin() {
        _lockState.value = AppLockState.PinRequired
    }

    /** Transition to Biometric requirement. */
    fun requireBiometric() {
        _lockState.value = AppLockState.BiometricRequired
    }
    
    val isLocked: Boolean get() = _lockState.value != AppLockState.Unlocked
}

package com.kavach.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * AuthState — Centralized operational status.
 * Prevents navigation race conditions by providing a single source of truth.
 */
enum class CapabilityLevel {
    FULL,           // <2h offline: All cached actions allowed
    RESTRICTED,     // <6h offline: Commands blocked, basic updates allowed
    READ_ONLY,      // <24h offline: Only viewing allowed
    LOCKED          // >24h offline: Force logout/lockout
}

sealed class AuthState {
    object Loading           : AuthState()
    object Unauthenticated   : AuthState()
    object NeedsConsent      : AuthState()
    object NeedsPermissions  : AuthState()
    object NeedsIntegrity    : AuthState()
    object Restricted        : AuthState()
    object AppLocked         : AuthState()
    object StartupTimeout    : AuthState()
    data class Authenticated(
        val token: String,
        val role: String,
        val pno: String,
        val isOffline: Boolean = false,
        val capability: CapabilityLevel = CapabilityLevel.FULL
    ) : AuthState()
}

/** ViewModel that handles session monitoring and recovery for KavachNavHost. */
@HiltViewModel
class NavHostViewModel @Inject constructor(
    val sessionDataStore : SessionDataStore,
    private val authRepo   : AuthRepository,
    val appLockManager     : com.kavach.app.core.security.AppLockManager,
    networkMonitor         : com.kavach.app.util.NetworkMonitor
) : ViewModel() {

    val connectionStatus = networkMonitor.status
        .distinctUntilChanged()
        .debounce(2000)

    private var startupJob: kotlinx.coroutines.Job? = null
    private var retryAttemptCount = 0
    private var lastRetryTime = 0L

    private val _startupTimeline = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val startupTimeline = _startupTimeline.asStateFlow()

    private fun updateTimeline(stage: String, success: Boolean) {
        _startupTimeline.update { it + (stage to success) }
    }

    init {
        startStartupTimer()
    }

    private fun startStartupTimer() {
        startupJob?.cancel()
        _isStartupTimedOut.value = false
        startupJob = viewModelScope.launch {
            kotlinx.coroutines.delay(8000)
            if (authState.value == AuthState.Loading) {
                Timber.w("Startup sequence timed out after 8s. Entering timeout recovery.")
                _isStartupTimedOut.value = true
            }
        }
    }

    private val _isRecovering = MutableStateFlow(false)
    val isRecovering = _isRecovering.asStateFlow()

    private val _isLimitedMode = MutableStateFlow(false)
    val isLimitedMode = _isLimitedMode.asStateFlow()

    private val _isStartupTimedOut = MutableStateFlow(false)

    /**
     * Centralized AuthState Flow.
     * Combines token, role, and integrity status to determine the exact app state.
     */
    val authState: StateFlow<AuthState> = combine(
        sessionDataStore.token,
        sessionDataStore.role,
        sessionDataStore.pno,
        sessionDataStore.isVerifiedInThisSession,
        sessionDataStore.consentAccepted,
        sessionDataStore.permissionsHandled,
        appLockManager.lockState,
        _isStartupTimedOut,
        sessionDataStore.integrityLevel,
        connectionStatus,
        sessionDataStore.lastAuthTime
    ) { args: Array<Any?> ->
        val token       = args[0] as? String
        val role        = args[1] as? String
        val pno         = args[2] as? String
        val isVerified  = args[3] as? Boolean ?: false
        val consent     = args[4] as? Boolean ?: false
        val permissions = args[5] as? Boolean ?: false
        val lockState   = args[6] as? com.kavach.app.core.security.AppLockState
        val timedOut    = args[7] as? Boolean ?: false
        val integrity   = args[8] as? String ?: ""
        val network     = args[9] as? com.kavach.app.util.ConnectionStatus
        val lastAuth    = args[10] as? Long ?: 0L

        // Update Timeline Stages
        if (token != null && _startupTimeline.value.none { it.first == "TOKEN" }) updateTimeline("TOKEN", true)
        if (!integrity.isNullOrBlank() && _startupTimeline.value.none { it.first == "INTEGRITY" }) updateTimeline("INTEGRITY", true)
        if (lockState == com.kavach.app.core.security.AppLockState.Unlocked && _startupTimeline.value.none { it.first == "LOCK" }) updateTimeline("LOCK", true)

        // Point 1 FIX: Cancel startup job if we are no longer loading
        if (token != null) {
            startupJob?.cancel()
        }

        when {
            // ── Point 5 FIX: Offline Resiliency takes precedence over Timeout ──
            network == com.kavach.app.util.ConnectionStatus.UNAVAILABLE && !token.isNullOrBlank() && integrity.isNotEmpty() -> 
                AuthState.Authenticated(token, role ?: "", pno ?: "", isOffline = true)

            // Loading state only until token is loaded (even if empty)
            token == null && !timedOut -> AuthState.Loading
            
            // 0. Startup Timeout (Fail-Safe)
            timedOut && token == null -> AuthState.StartupTimeout

            // 0. App Lock Check (Priority: check if locally locked first if we have a session)
            !token.isNullOrEmpty() && lockState != com.kavach.app.core.security.AppLockState.Unlocked -> AuthState.AppLocked

            // 1. Consent Check (default to false if null)
            consent == false -> AuthState.NeedsConsent
            
            // 2. Permission Check (default to false if null)
            permissions == false -> AuthState.NeedsPermissions
            
            // 3. Auth Check
            token.isEmpty() -> AuthState.Unauthenticated
            
            // 4. Integrity Check (Persisted integrity allows offline entry handled above)
            !isVerified && network == com.kavach.app.util.ConnectionStatus.AVAILABLE -> AuthState.NeedsIntegrity
            
            // Point 2 FIX: Progressive Degradation logic
            network == com.kavach.app.util.ConnectionStatus.UNAVAILABLE && !token.isNullOrBlank() && integrity.isNotEmpty() -> {
                val ageMs = System.currentTimeMillis() - lastAuth
                val ageHr = ageMs / (1000 * 60 * 60)
                
                when {
                    ageHr >= 24 -> AuthState.Unauthenticated
                    ageHr >= 6  -> AuthState.Authenticated(token, role ?: "", pno ?: "", isOffline = true, capability = CapabilityLevel.READ_ONLY)
                    ageHr >= 2  -> AuthState.Authenticated(token, role ?: "", pno ?: "", isOffline = true, capability = CapabilityLevel.RESTRICTED)
                    else        -> AuthState.Authenticated(token, role ?: "", pno ?: "", isOffline = true, capability = CapabilityLevel.FULL)
                }
            }

            // 6. Fully Authenticated (Online)
            else -> AuthState.Authenticated(token, role ?: "", pno ?: "", isOffline = false, capability = CapabilityLevel.FULL)
        }.also { state ->
            // Point 1 FIX: Production-Safe Sanity Assertions
            if (state is AuthState.AppLocked && token == null) {
                Timber.e("INVARIANT VIOLATION: AppLocked without token")
            }
            if (state is AuthState.StartupTimeout && token != null) {
                Timber.e("INVARIANT VIOLATION: StartupTimeout with token")
            }
        }
    }.stateIn(
        scope   = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.Loading
    )

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val sessionBreachReason: StateFlow<String?> = sessionDataStore.sessionBreachReason
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun syncProfile(silent: Boolean = false): Boolean {
        if (_isRecovering.value) return false
        
        return try {
            if (!silent) _isRecovering.value = true
            if (!silent) _error.value = null

            when (val result = authRepo.syncProfile()) {
                is com.kavach.app.utils.ApiResult.Success -> {
                    sessionDataStore.markAsVerified()
                    _isLimitedMode.value = false
                    true
                }
                is com.kavach.app.utils.ApiResult.Unauthorized -> {
                    Timber.w("Unauthorized during sync: ${result.message}")
                    if (!silent) {
                        _isLimitedMode.value = true
                        _error.value = "सत्र सत्यापित नहीं किया जा सका। सीमित मोड सक्रिय।"
                    }
                    false
                }
                else -> {
                    if (!silent) _error.value = "Network Error: Unable to verify role"
                    false
                }
            }
        } catch (e: Exception) {
            if (!silent) _error.value = "Network Error: ${e.localizedMessage}"
            false
        } finally {
            if (!silent) _isRecovering.value = false
        }
    }

    fun enterLimitedMode() {
        _isLimitedMode.value = true
        _isRecovering.value = false
        _error.value = "सीमित मोड सक्रिय है (Limited Mode Active)"
    }

    fun manualRetry() {
        _error.value = null // Clear old errors
        val now = System.currentTimeMillis()
        val baseCooldown = when(retryAttemptCount) {
            0 -> 0L
            1 -> 2000L
            2 -> 5000L
            else -> 10000L
        }
        
        // Point 3 FIX: Randomized Exponential Jitter (±0-3s)
        val jitter = if (baseCooldown > 0) (0..3000).random().toLong() else 0L
        val effectiveCooldown = baseCooldown + jitter

        if (now - lastRetryTime < effectiveCooldown) {
            _error.value = "कृपया प्रतीक्षा करें (${((effectiveCooldown - (now - lastRetryTime))/1000) + 1}s)"
            return
        }

        lastRetryTime = now
        retryAttemptCount++
        
        startStartupTimer()
        viewModelScope.launch {
            syncProfile()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _sessionBreachReason.value = null
            sessionDataStore.markAsUnverified()
            sessionDataStore.clearSession()
        }
    }

    fun clearBreach() {
        _sessionBreachReason.value = null
    }
}

package com.kavach.app.core.auth

import com.kavach.app.data.local.SessionDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val pno: String, val role: String, val token: String) : AuthState()
    data class SessionBreached(val reason: String) : AuthState()
}

/**
 * Single Source of Truth for Authentication State.
 * Orchestrates session data and exposes a unified [AuthState] for the UI and Navigation.
 */
@Singleton
class AuthManager @Inject constructor(
    private val sessionDataStore: SessionDataStore
) {
    val authState: Flow<AuthState> = combine(
        sessionDataStore.token,
        sessionDataStore.pno,
        sessionDataStore.role,
        sessionDataStore.sessionBreachReason
    ) { token, pno, role, breachReason ->
        if (breachReason != null) {
            AuthState.SessionBreached(breachReason)
        } else if (token.isNullOrBlank()) {
            AuthState.Unauthenticated
        } else {
            AuthState.Authenticated(
                pno = pno.ifBlank { "UNKNOWN" },
                role = role.ifBlank { "USER" },
                token = token
            )
        }
    }

    suspend fun logout() {
        sessionDataStore.clearSession()
    }

    suspend fun lockSession(reason: String) {
        sessionDataStore.setSessionBreach(reason)
        sessionDataStore.clearSession()
        sessionDataStore.lockApp()
    }

    suspend fun restoreSession() {
        // Validation logic can be added here if needed
    }
}

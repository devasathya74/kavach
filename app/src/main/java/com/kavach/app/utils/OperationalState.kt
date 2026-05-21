package com.kavach.app.utils

/**
 * OperationalUiState — A generic state machine for operational modules.
 * Ensures consistent handling of loading, success, and error states across the app.
 */
sealed interface OperationalUiState<out T> {
    object Idle : OperationalUiState<Nothing>
    object Loading : OperationalUiState<Nothing>
    data class Success<T>(val data: T) : OperationalUiState<T>
    data class Error(val message: String) : OperationalUiState<Nothing>
}

/**
 * OperationalActionState — Tracks the status of asynchronous mutations (Create, Update, Delete).
 * Supports optimistic UI and rollback reporting.
 */
sealed interface OperationalActionState {
    object Idle : OperationalActionState
    object Processing : OperationalActionState
    data class Success(val message: String, val transactionId: String? = null) : OperationalActionState
    data class Error(val message: String, val rollbackRequired: Boolean = false) : OperationalActionState
}

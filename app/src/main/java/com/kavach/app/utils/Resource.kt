package com.kavach.app.utils

/**
 * Generic sealed class wrapping API results.
 * Used across all layers to propagate success/error/loading state.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val code: Int? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

/**
 * Extension to safely wrap a suspend call and convert exceptions to Resource.Error.
 */
suspend fun <T> safeCall(block: suspend () -> Resource<T>): Resource<T> {
    return try {
        block()
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Unknown error")
    }
}

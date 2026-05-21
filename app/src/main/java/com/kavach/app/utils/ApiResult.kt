package com.kavach.app.utils

/**
 * Operational Result Wrapper - Hardened for Field Stress.
 * Propagates deterministic states across all app layers.
 */
sealed class ApiResult<out T> {
    
    data class Success<T>(val data: T) : ApiResult<T>()
    
    /**
     * Standard failure with message and optional HTTP code.
     */
    data class Error(
        val message: String, 
        val code: Int? = null,
        val throwable: Throwable? = null
    ) : ApiResult<Nothing>()

    /**
     * Platform-level Access Denial (Token expired, Device revoked).
     * Triggers immediate re-auth or lockout behavior.
     */
    data class Unauthorized(val message: String = "Session expired or device revoked") : ApiResult<Nothing>()

    /**
     * Network Partition / Connectivity Failure.
     * Triggers offline-first / local-draft behavior.
     */
    data class Offline(val message: String = "No network connection") : ApiResult<Nothing>()

    /**
     * Revision Drift / Optimistic Concurrency Failure.
     * Triggers manual reconciliation or 'Stale Data' warnings.
     */
    data class Conflict(val message: String = "Revision mismatch. Data out of sync.") : ApiResult<Nothing>()

    object Loading : ApiResult<Nothing>()
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(action: (ApiResult.Error) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(this)
    return this
}

inline fun <T> ApiResult<T>.onUnauthorized(action: () -> Unit): ApiResult<T> {
    if (this is ApiResult.Unauthorized) action()
    return this
}

/**
 * Higher-order function to safely wrap network calls into ApiResult.
 * Includes exponential backoff retry logic (Limited to 3 attempts) to prevent server DDOS.
 */
suspend fun <T> safeApiCall(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    block: suspend () -> ApiResult<T>
): ApiResult<T> {
    var currentDelay = initialDelay
    repeat(maxRetries) { attempt ->
        try {
            val result = block()
            if (result !is ApiResult.Offline || attempt == maxRetries - 1) {
                return result
            }
        } catch (e: java.io.IOException) {
            if (attempt == maxRetries - 1) return ApiResult.Offline("Connectivity error: ${e.localizedMessage}")
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> return ApiResult.Unauthorized()
                403 -> return ApiResult.Unauthorized("Access Denied (403)")
                409 -> return ApiResult.Conflict()
                429 -> { /* Too many requests, wait and retry */ }
                503 -> return ApiResult.Error("🛠️ सर्वर में रखरखाव (Maintenance) चल रहा है। कृपया कुछ देर बाद प्रयास करें।", 503, e)
                in 500..599 -> { /* Server error, retry */ }
                else -> return ApiResult.Error("HTTP Error: ${e.code()}", e.code(), e)
            }
            if (attempt == maxRetries - 1) return ApiResult.Error("HTTP Error: ${e.code()}", e.code(), e)
        } catch (e: Exception) {
            return ApiResult.Error(e.localizedMessage ?: "Unknown failure", throwable = e)
        }
        
        // Exponential Backoff
        kotlinx.coroutines.delay(currentDelay)
        currentDelay *= 2
    }
    return ApiResult.Error("Operation failed after $maxRetries attempts")
}

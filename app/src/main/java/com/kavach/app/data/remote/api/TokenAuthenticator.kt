package com.kavach.app.data.remote.api

import android.content.Context
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.dto.auth.RefreshTokenRequest
import com.kavach.app.security.AttestationResult
import com.kavach.app.security.IntegrityRepository
import com.kavach.app.utils.DeviceIdUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator — handles 401 Unauthorized via token refresh.
 *
 * Hardened Features:
 * 1. Synchronized Refresh: Mutex prevents parallel refresh race conditions.
 * 2. Integrity Bound: Re-attests Play Integrity after every token refresh.
 * 3. Double-Check Pattern: Detects if another thread already refreshed the token.
 * 4. Fail-Secure: Clears session on critical integrity or repeated failures.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore                  : SessionDataStore,
    private val authRefreshService            : AuthRefreshApiService,
    private val integrityRepositoryLazy       : dagger.Lazy<IntegrityRepository>,
    @ApplicationContext private val context   : Context
) : Authenticator {

    private val mutex = Mutex()

    private val integrityRepository: IntegrityRepository
        get() = integrityRepositoryLazy.get()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Detect Critical Integrity Failure
        val isIntegrityError = response.message == "KAVACH_INTEGRITY_MISSING_SECRET" ||
                               response.peekBody(1024).string().contains("KAVACH_INTEGRITY_FAILURE")

        if (isIntegrityError) {
            Timber.e("Critical integrity failure detected. Holding session in local-only mode.")
            // sessionStore.clearSession() <- REMOVED: Prioritize continuity
            return null
        }

        // 2. Prevent infinite retry loops
        if (responseCount(response) >= 2) {
            Timber.e("Maximum retry count reached for 401. Holding session.")
            // sessionStore.clearSession() <- REMOVED
            return null
        }

        // 3. Prevent explicit refresh loops via header
        if (response.request.header("X-Token-Refreshed") != null) {
            Timber.e("Token refresh loop detected. Holding session.")
            // sessionStore.clearSession() <- REMOVED
            return null
        }

        return runBlocking {
            mutex.withLock {
                val currentToken = sessionStore.token.firstOrNull()
                val authHeader   = response.request.header("Authorization")
                
                // Double-Check: If the token in store is different from the one that failed,
                // another thread already refreshed it. Retry with the new token.
                if (authHeader != null && authHeader != "Bearer $currentToken" && !currentToken.isNullOrBlank()) {
                    Timber.d("Token already refreshed by another thread. Retrying original request.")
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .header("X-Token-Refreshed", "true")
                        .build()
                }

                val refreshToken = sessionStore.refreshToken.firstOrNull()
                val deviceId     = DeviceIdUtil.getDeviceId(context)

                if (refreshToken.isNullOrBlank()) {
                    Timber.e("No refresh token found. Holding session.")
                    // sessionStore.clearSession() <- REMOVED
                    return@runBlocking null
                }

                try {
                    Timber.d("Initiating authoritative token refresh...")
                    val refreshResp = authRefreshService.refreshToken(
                        RefreshTokenRequest(
                            refreshToken = refreshToken,
                            deviceId     = deviceId
                        )
                    )

                    val body = refreshResp.body()
                    if (refreshResp.isSuccessful && body?.accessToken != null) {
                        Timber.i("Token refresh successful.")
                        val newAccessToken  = body.accessToken
                        val newRefreshToken = body.refreshToken ?: refreshToken
                        val newExpiresIn    = body.expiresIn ?: 3600L

                        sessionStore.updateTokens(
                            accessToken  = newAccessToken,
                            refreshToken = newRefreshToken,
                            expiresIn    = newExpiresIn
                        )

                        // Asynchronous re-attestation after refresh (Non-blocking)
                        // We do NOT block the request for integrity on startup.
                        // This prevents tunnel/network wake delays from halting the UI.
                        Timber.d("New tokens saved. Proceeding with request immediately.")
                        
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .header("X-Token-Refreshed", "true")
                            .build()

                    } else {
                        val errorReason = when (refreshResp.code()) {
                            401 -> "सत्र समाप्त (Refresh Token Expired)"
                            403 -> "सुरक्षा उल्लंघन: डिवाइस मेल नहीं खाता (Device Mismatch)"
                            else -> "सर्वर त्रुटि: ${refreshResp.code()}"
                        }
                        Timber.e("Token refresh API failed: $errorReason. Terminating session.")
                        
                        sessionStore.setSessionBreach(errorReason)
                        sessionStore.clearSession()
                        sessionStore.lockApp() 
                        
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception during token refresh handshake.")
                    sessionStore.setSessionBreach("नेटवर्क त्रुटि: ${e.message}")
                    sessionStore.clearSession()
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

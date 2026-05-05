package com.kavach.app.data.remote.api

import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.dto.RefreshTokenRequest
import com.kavach.app.utils.DeviceIdUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator — called automatically when the server returns 401.
 *
 * Flow:
 *  1. Grab the stored refresh token.
 *  2. Call the refresh endpoint (using a separate OkHttpClient with NO auth interceptor).
 *  3a. Success → update DataStore tokens, retry original request with new access token.
 *  3b. Failure (refresh expired / device_mismatch) → clear session, return null
 *      → app will be navigated to login by SessionExpiredEvent.
 *
 * Thread safety: `runBlocking` is used here intentionally — OkHttp Authenticator
 * runs on a background thread, not the main thread.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore        : SessionDataStore,
    private val authRefreshService  : AuthRefreshApiService,
    @ApplicationContext private val context: Context
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite retry loops — if we already tried refreshing, give up
        if (response.request.header("X-Token-Refreshed") != null) {
            runBlocking { sessionStore.clearSession() }
            return null
        }

        return runBlocking {
            val refreshToken = sessionStore.refreshToken.firstOrNull()
            val deviceId     = DeviceIdUtil.getDeviceId(context)

            if (refreshToken.isNullOrBlank()) {
                sessionStore.clearSession()
                return@runBlocking null
            }

            try {
                val refreshResp = authRefreshService.refreshToken(
                    RefreshTokenRequest(
                        refreshToken = refreshToken,
                        deviceId     = deviceId
                    )
                )

                if (refreshResp.isSuccessful && refreshResp.body()?.accessToken != null) {
                    val body = refreshResp.body()!!
                    val newAccessToken = body.accessToken!!
                    val newRefreshToken = body.refreshToken ?: refreshToken
                    val newExpiresIn = body.expiresIn ?: 3600L
                    
                    // Persist new tokens
                    sessionStore.updateTokens(
                        accessToken  = newAccessToken,
                        refreshToken = newRefreshToken,
                        expiresIn    = newExpiresIn
                    )
                    // Retry the original request with the new access token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .header("X-Token-Refreshed", "true")   // prevent retry loop
                        .build()
                } else {
                    // 401/403 on refresh → session invalid (device changed / token revoked)
                    sessionStore.clearSession()
                    null
                }
            } catch (e: Exception) {
                // Network error during refresh → don't clear session, let user retry
                null
            }
        }
    }
}

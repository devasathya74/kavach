package com.kavach.app.data.remote.api

import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.dto.RefreshTokenRequest
import com.kavach.app.security.AttestationResult
import com.kavach.app.security.IntegrityRepository
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
 * ISSUE 1 FIX: Re-attestation deadlock prevention.
 *
 * OLD (dangerous):
 *   attestation fail → clearSession() → logout
 *   This locked out officers in areas with weak Play Services / poor network.
 *
 * NEW (resilient):
 *   ┌─────────────────────────────────────┬──────────────────────────────────┐
 *   │ State                               │ Action                           │
 *   ├─────────────────────────────────────┼──────────────────────────────────┤
 *   │ STRONG / DEVICE (attestation pass)  │ Refresh + update + retry         │
 *   │ DEGRADED (network / API timeout)    │ Refresh + use old level + retry  │
 *   │ BASIC (rooted-ish)                  │ Refresh + restricted mode        │
 *   │ FAILED (tampered APK / rooted)      │ clearSession → force re-login    │
 *   │ Repeated fail (≥ 2 retries)         │ clearSession → force re-login    │
 *   └─────────────────────────────────────┴──────────────────────────────────┘
 *
 * The key insight:
 *   - FAILED = hard security failure (device tampered) → logout is correct.
 *   - DEGRADED = network problem (Google Play API unreachable) → NOT a security failure.
 *     Officers in poor network areas should NOT be locked out by a slow API call.
 *   - BASIC = restricted mode, but session is valid → continue with limited access.
 *
 * FIX 6: Re-attestation on EVERY token refresh (kept from v2).
 * ISSUE 1: DEGRADED no longer = immediate logout.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore                  : SessionDataStore,
    private val authRefreshService            : AuthRefreshApiService,
    private val integrityRepositoryLazy       : dagger.Lazy<IntegrityRepository>,
    @ApplicationContext private val context   : Context
) : Authenticator {

    // Resolved lazily on first use — breaks the DI cycle:
    // TokenAuthenticator → Lazy<IntegrityRepository> (no cycle at construction time)
    // IntegrityRepository → KavachApiService → OkHttpClient(authed) → TokenAuthenticator
    // The Lazy wrapper defers IntegrityRepository resolution until after full graph is built.
    private val integrityRepository: IntegrityRepository
        get() = integrityRepositoryLazy.get()


    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Detect Critical Integrity Failure — Force Re-authentication
        val isIntegrityError = response.message == "KAVACH_INTEGRITY_MISSING_SECRET" ||
                              response.peekBody(1024).string().contains("KAVACH_INTEGRITY_FAILURE")

        if (isIntegrityError) {
            runBlocking { sessionStore.clearSession() }
            return null
        }

        // 2. Prevent infinite retry loops (Retry Guard)
        if (responseCount(response) >= 2) {
            runBlocking { sessionStore.clearSession() }
            return null
        }

        // 3. Prevent explicit retry loops via header
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
                    val body            = refreshResp.body()!!
                    val newAccessToken  = body.accessToken!!
                    val newRefreshToken = body.refreshToken ?: refreshToken
                    val newExpiresIn    = body.expiresIn ?: 3600L

                    // Persist new tokens first
                    sessionStore.updateTokens(
                        accessToken  = newAccessToken,
                        refreshToken = newRefreshToken,
                        expiresIn    = newExpiresIn
                    )

                    // FIX 6 + ISSUE 1: Re-attest AFTER every token refresh.
                    // But handle each result correctly — DEGRADED is NOT a failure.
                    when (val attestation = integrityRepository.attest()) {

                        is AttestationResult.Failed -> {
                            // ISSUE 1 FIX: FAILED = tampered device / rooted post-refresh.
                            // This IS a security failure → clear session → force re-login.
                            // (This is the ONLY case where we log out on attestation.)
                            sessionStore.clearSession()
                            return@runBlocking null
                        }

                        is AttestationResult.Degraded -> {
                            // ISSUE 1 FIX: DEGRADED = Google Play API unreachable (network problem).
                            // This is NOT a security failure. The officer is in a poor-signal area.
                            // Backend DEGRADED trust window (5 min) will force re-attest soon.
                            // DO NOT log out. Continue with DEGRADED flag — UI shows limited mode.
                            sessionStore.saveIntegrityLevel("DEGRADED")
                            // Fall through to retry the original request
                        }

                        is AttestationResult.Restricted -> {
                            // BASIC level: session valid, device is restricted.
                            // UI will show "limited mode" — no sensitive ops.
                            sessionStore.saveIntegrityLevel(attestation.level)
                        }

                        is AttestationResult.Passed -> {
                            sessionStore.saveIntegrityLevel(attestation.verdict.integrityLevel)
                        }
                    }

                    // Retry the original request with the new access token
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .header("X-Token-Refreshed", "true")   // prevent retry loop
                        .build()

                } else {
                    sessionStore.clearSession()
                    null
                }
            } catch (e: Exception) {
                // Network exception during refresh itself — do NOT clear session.
                // This is a transient error. OkHttp retry logic will handle it.
                // Only clear session on explicit 401 from server (handled above).
                null
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

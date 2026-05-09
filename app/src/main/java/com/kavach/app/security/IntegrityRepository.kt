package com.kavach.app.security

import android.content.Context
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.auth.IntegrityVerdict
import com.kavach.app.data.remote.dto.auth.IntegrityVerifyRequest
import com.kavach.app.utils.DeviceIdUtil
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ╔═══════════════════════════════════════════════════════════════╗
 * ║         KAVACH — INTEGRITY ATTESTATION REPOSITORY            ║
 * ╠═══════════════════════════════════════════════════════════════╣
 * ║  Full Attestation Flow:                                      ║
 * ║                                                              ║
 * ║  Android App                  Backend               Google   ║
 * ║    │                            │                      │     ║
 * ║    │──POST /nonce──────────────►│                      │     ║
 * ║    │◄──nonce (bound)────────────│                      │     ║
 * ║    │                            │                      │     ║
 * ║    │──requestIntegrityToken()──►│ (Google Play SDK)    │     ║
 * ║    │◄──opaque_token─────────────│                      │     ║
 * ║    │                            │                      │     ║
 * ║    │──POST /verify (token)─────►│──decodeToken()──────►│     ║
 * ║    │                            │◄──verdict────────────│     ║
 * ║    │◄──{allowed, level}─────────│                      │     ║
 * ║    │                            │                      │     ║
 * ╚═══════════════════════════════════════════════════════════════╝
 *
 *  POLICY (enforced by backend, not this class):
 *  ┌──────────────────────────────────┬──────────────────────────┐
 *  │ MEETS_STRONG_INTEGRITY           │ Full operational access  │
 *  │ MEETS_DEVICE_INTEGRITY           │ Full operational access  │
 *  │ MEETS_BASIC_INTEGRITY only       │ Restricted mode          │
 *  │ FAILED / UNKNOWN                 │ Block sensitive ops      │
 *  └──────────────────────────────────┴──────────────────────────┘
 *
 *  Token Validity Window: 30 minutes (re-attest on refresh).
 *  Offline Degradation: Falls back to signed trust window — NOT full access.
 */
@Singleton
class IntegrityRepository @Inject constructor(
    private val apiLazy          : dagger.Lazy<KavachApiService>,
    private val integrityManager : KavachIntegrityManager,
    @ApplicationContext private val context: Context
) {
    private val api: KavachApiService
        get() = apiLazy.get()

    /**
     * Runs a full attestation cycle:
     * 1. Fetch bound nonce from server
     * 2. Request integrity token from Google Play
     * 3. Send opaque token to backend for Google-side verification
     * 4. Return the verdict (allowed/level)
     *
     * Fail-secure: any failure returns AttestationResult.Failed
     */
    suspend fun attest(): AttestationResult {
        return try {
            Timber.d("Starting integrity attestation cycle...")
            
            // Step 1: Get a server-bound nonce
            val nonceResp = api.getIntegrityNonce()
            val nonceData = nonceResp.body()?.data
            if (!nonceResp.isSuccessful || nonceData == null) {
                Timber.e("Integrity Error: Nonce fetch failed [${nonceResp.code()}]")
                return AttestationResult.Failed(
                    "Nonce fetch failed [${nonceResp.code()}] — cannot proceed"
                )
            }
            Timber.d("Attestation: Nonce received.")

            // Step 2: Request Google Play integrity token
            val integrityResult = integrityManager.requestIntegrityToken(nonceData.nonce)
            if (integrityResult is IntegrityResult.Error) {
                Timber.e("Integrity Error: Google Play token request failed: ${integrityResult.reason}")
                return AttestationResult.Failed(integrityResult.reason)
            }
            val token = (integrityResult as IntegrityResult.Success).token
            Timber.d("Attestation: Token generated.")

            // Step 3: Backend verifies with Google
            val deviceId   = DeviceIdUtil.getDeviceId(context)
            val verifyResp = api.verifyIntegrityToken(
                IntegrityVerifyRequest(
                    integrityToken = token,
                    requestId      = nonceData.requestId,
                    deviceId       = deviceId
                )
            )

            val verdict = verifyResp.body()?.data
            if (!verifyResp.isSuccessful || verdict == null) {
                Timber.e("Integrity Error: Backend verification failed [${verifyResp.code()}]")
                return AttestationResult.Failed(
                    "Backend verification failed [${verifyResp.code()}]"
                )
            }

            Timber.d("Attestation Result: ${verdict.integrityLevel} (Blocked=${verdict.blocked}, Restricted=${verdict.restricted})")

            when {
                verdict.blocked -> AttestationResult.Failed(
                    verdict.message ?: "Device blocked by integrity policy"
                )
                verdict.restricted -> AttestationResult.Restricted(
                    level   = verdict.integrityLevel,
                    message = verdict.message
                )
                else -> AttestationResult.Passed(verdict)
            }

        } catch (e: Exception) {
            Timber.w(e, "Attestation Degraded: Network or API failure")
            AttestationResult.Degraded("Network unavailable: ${e.message}")
        }
    }
}

/**
 * Result of attestation — what the app layer acts on.
 *
 * CRITICAL: App must NEVER grant full access on Degraded or Restricted.
 */
sealed class AttestationResult {
    /** Full access granted — STRONG or DEVICE integrity confirmed. */
    data class Passed(val verdict: IntegrityVerdict) : AttestationResult()

    /**
     * Restricted — BASIC_INTEGRITY only.
     * Allowed to login but cannot access sensitive operations.
     */
    data class Restricted(
        val level   : String,
        val message : String?
    ) : AttestationResult()

    /**
     * Hard failure — rooted device detected, tampered APK, or invalid token.
     * App MUST NOT proceed to operational screens.
     */
    data class Failed(val reason: String) : AttestationResult()

    /**
     * Network unavailable — Google Play API couldn't be reached.
     * This is a degraded state, NOT a pass.
     * Backend controls how long to honor the last valid attestation.
     */
    data class Degraded(val reason: String) : AttestationResult()
}

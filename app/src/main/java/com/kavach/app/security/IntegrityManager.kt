package com.kavach.app.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║            KAVACH — PLAY INTEGRITY ATTESTATION              ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Architecture Rule (CRITICAL):                              ║
 * ║  ┌─────────────────────────────────────────────────────┐   ║
 * ║  │  Client NEVER interprets the verdict.               │   ║
 * ║  │  Client ONLY fetches a token and sends to backend.  │   ║
 * ║  │  Backend calls Google servers → makes the decision. │   ║
 * ║  └─────────────────────────────────────────────────────┘   ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  When to attest:                                            ║
 * ║  ① Login (OTP + Admin)                                      ║
 * ║  ② Every token refresh                                     ║
 * ║  ③ Sensitive actions (not implemented here, future)        ║
 * ║  ④ Periodic re-attestation (30-min window)                 ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Integrity Level Policy (enforced by backend):
 *  MEETS_STRONG_INTEGRITY → Full access
 *  MEETS_DEVICE_INTEGRITY → Full access
 *  BASIC_INTEGRITY only   → Restricted mode
 *  Failed / Unknown       → Block sensitive operations
 */
@Singleton
class KavachIntegrityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Cloud project number from Google Play Console
        // ✏️ EDIT: Replace with your actual Google Cloud Project Number
        // Found at: play.google.com/console → Setup → API access → Cloud project number
        private const val CLOUD_PROJECT_NUMBER = 0L // TODO: Replace with actual project number
    }

    private val integrityManager by lazy {
        IntegrityManagerFactory.create(context)
    }

    /**
     * Requests an integrity token from Google Play.
     *
     * @param nonce  A SHA-256 bound nonce from the backend.
     *               Format: sha256(userId + deviceId + requestId + timestamp)
     *               This MUST come from the server — never generate client-side.
     *
     * @return [IntegrityResult] — token on success, or error with reason.
     */
    suspend fun requestIntegrityToken(nonce: String): IntegrityResult {
        return try {
            if (nonce.isBlank()) {
                return IntegrityResult.Error("Nonce cannot be empty — server did not provide a valid nonce")
            }

            val tokenResponse = integrityManager.requestIntegrityToken(
                IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .apply {
                        // Use cloud project number if available (standard check)
                        // Without it, falls back to classic check (still valid)
                        if (CLOUD_PROJECT_NUMBER != 0L) {
                            setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                        }
                    }
                    .build()
            ).await()

            IntegrityResult.Success(tokenResponse.token())

        } catch (e: Exception) {
            // Integrity request failed — treat as UNVERIFIED (fail-secure)
            IntegrityResult.Error("Integrity check failed: ${e.message}")
        }
    }
}

/**
 * Sealed result for integrity token operations.
 * The token itself is opaque — it must be sent to backend for decoding.
 */
sealed class IntegrityResult {
    /** Attestation token ready to be sent to backend for verification. */
    data class Success(val token: String) : IntegrityResult()

    /**
     * Attestation failed — backend should treat this as UNVERIFIED.
     * Do NOT allow access on failure (fail-secure principle).
     */
    data class Error(val reason: String) : IntegrityResult()
}

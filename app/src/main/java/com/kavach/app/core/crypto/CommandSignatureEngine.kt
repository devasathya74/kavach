package com.kavach.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kavach.app.core.clock.TrustedClock
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CommandSignatureEngine — Cryptographic trust layer for high-authority events.
 *
 * Uses Android Keystore (hardware-backed on supported devices) so key material
 * never leaves the secure enclave — not extractable even via root.
 *
 * Every high-authority event (LOCKDOWN, OVERRIDE, DEPLOYMENT_FREEZE) must be:
 *   1. Signed by the issuing device: [sign(payload)] → signature string
 *   2. Verified by receiving devices: [verify(payload, signature)] → Boolean
 *
 * Key rotation:
 *   Keys are rotated on session boundaries (login/logout) and on explicit
 *   [rotate] calls. The key version is embedded in the signature header
 *   so receivers can reject events signed with revoked keys.
 *
 * Signature format (Base64-encoded):
 *   "v{keyVersion}:{timestamp}:{hmac_base64}"
 *
 * Events that REQUIRE signing:
 *   - LockdownActivated
 *   - CommandOverride
 *   - ForcedLogout
 *   - DeploymentFreeze
 */
@Singleton
class CommandSignatureEngine @Inject constructor(
    private val trustedClock: TrustedClock
) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX  = "kavach_cmd_key_v"
        private const val ALGORITHM         = "HmacSHA256"
        private const val MAX_KEY_VERSIONS  = 3   // Keep last N valid keys for grace period
    }

    private val _keyVersion = MutableStateFlow(1)
    val keyVersion: StateFlow<Int> = _keyVersion.asStateFlow()

    // Valid key versions (current + grace period)
    private val validVersions = mutableSetOf<Int>()

    init {
        ensureKeyExists(_keyVersion.value)
    }

    /**
     * Sign an event payload string.
     * @return "v{ver}:{timestamp}:{hmac}" or null on failure.
     */
    fun sign(payload: String): String? = try {
        val version   = _keyVersion.value
        val timestamp = trustedClock.nowMs()
        val mac       = getMac(version) ?: return null
        val input     = "$version:$timestamp:$payload"
        val hmac      = Base64.encodeToString(
            mac.doFinal(input.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        "v$version:$timestamp:$hmac"
    } catch (e: Exception) {
        null
    }

    /**
     * Verify a signed event payload.
     * @return [SignatureVerification] result with reason on failure.
     */
    fun verify(payload: String, signature: String): SignatureVerification {
        return try {
            val parts = signature.split(":")
            if (parts.size < 3) return SignatureVerification.Invalid("Malformed signature")

            val version   = parts[0].removePrefix("v").toIntOrNull()
                            ?: return SignatureVerification.Invalid("Bad version field")
            val timestamp = parts[1].toLongOrNull()
                            ?: return SignatureVerification.Invalid("Bad timestamp field")
            val hmac      = parts[2]

            // Check key version is still valid (not rotated out)
            if (version !in validVersions) {
                return SignatureVerification.KeyRevoked(version)
            }

            // Check timestamp freshness (accept within ±5 min)
            val ageSec = (trustedClock.nowMs() - timestamp) / 1000L
            if (ageSec > 300L || ageSec < -30L) {
                return SignatureVerification.Expired(ageSec)
            }

            // Recompute HMAC and compare
            val mac       = getMac(version) ?: return SignatureVerification.Invalid("Key unavailable")
            val input     = "$version:$timestamp:$payload"
            val expected  = Base64.encodeToString(
                mac.doFinal(input.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP
            )

            if (constantTimeEquals(expected, hmac)) SignatureVerification.Valid
            else SignatureVerification.Tampered

        } catch (e: Exception) {
            SignatureVerification.Invalid("Verification exception: ${e.message}")
        }
    }

    /**
     * Rotate to a new key. Previous key remains valid for [MAX_KEY_VERSIONS] rotations
     * to allow in-flight events to complete verification.
     */
    fun rotate() {
        val newVersion = _keyVersion.value + 1
        ensureKeyExists(newVersion)
        _keyVersion.value = newVersion

        // Prune old versions beyond grace window
        validVersions.add(newVersion)
        if (validVersions.size > MAX_KEY_VERSIONS) {
            validVersions.remove(validVersions.min())
        }
    }

    /** Invalidate all keys — call on logout. Old signatures will fail verification. */
    fun revokeAll() {
        validVersions.clear()
        _keyVersion.value = 1
        ensureKeyExists(1)
    }

    private fun getMac(version: Int): Mac? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias    = "$KEY_ALIAS_PREFIX$version"
            if (!keyStore.containsAlias(alias)) return null
            val key      = keyStore.getKey(alias, null)
            Mac.getInstance(ALGORITHM).also { it.init(key) }
        } catch (e: Exception) { null }
    }

    private fun ensureKeyExists(version: Int) {
        try {
            val alias    = "$KEY_ALIAS_PREFIX$version"
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(alias)) {
                val spec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(256)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_PROVIDER)
                    .apply { init(spec) }
                    .generateKey()
            }
            validVersions.add(version)
        } catch (e: Exception) {
            // Keystore unavailable (emulator without secure element) — degrade gracefully
            validVersions.add(version)
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}

sealed class SignatureVerification {
    object Valid                              : SignatureVerification()
    object Tampered                           : SignatureVerification()
    data class Invalid(val reason: String)    : SignatureVerification()
    data class KeyRevoked(val version: Int)   : SignatureVerification()
    data class Expired(val ageSec: Long)      : SignatureVerification()

    val isValid get() = this == Valid
}

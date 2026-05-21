package com.kavach.app.data.remote.dto.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Login / Auth DTOs ─────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "pno")      val pno: String,
    @Json(name = "password") val password: String,
    @Json(name = "device_id") val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminLoginRequest(
    @Json(name = "pno")        val pno: String,
    @Json(name = "password")   val password: String,
    @Json(name = "device_id")  val deviceId: String? = null,
    @Json(name = "admin_code") val adminCode: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "status")         val status: String,
    @Json(name = "token")          val token: String? = null,
    @Json(name = "refresh_token")  val refreshToken: String? = null,
    @Json(name = "expires_in")     val expiresIn: Long? = null,
    @Json(name = "user")           val user: UserDto? = null,
    @Json(name = "device_secret")  val deviceSecret: String? = null,
    @Json(name = "message")        val message: String? = null,
    @Json(name = "success")        val success: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OtpRequest(
    @Json(name = "pno")  val pno: String,
    @Json(name = "otp")  val otp: String,
    @Json(name = "device_id") val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class ConsentRequest(
    @Json(name = "consent_type")    val consentType: String,
    @Json(name = "accepted")        val accepted: Boolean,
    @Json(name = "device_id")       val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "device_id")     val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class RefreshTokenResponse(
    @Json(name = "access_token")  val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in")    val expiresIn: Long? = null
)

// ── Integrity DTOs ────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class IntegrityNonceResponse(
    @Json(name = "nonce")      val nonce: String,
    @Json(name = "expires_at") val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class IntegrityVerifyRequest(
    @Json(name = "token")           val token: String? = null,
    @Json(name = "integrity_token") val integrityToken: String? = null,
    @Json(name = "nonce")           val nonce: String? = null,
    @Json(name = "request_id")      val requestId: String? = null,
    @Json(name = "device_id")       val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class IntegrityVerdict(
    @Json(name = "level")             val level: String,
    @Json(name = "integrity_level")   val integrityLevel: String? = null,
    @Json(name = "device_integrity")  val deviceIntegrity: List<String> = emptyList(),
    @Json(name = "app_integrity")     val appIntegrity: String? = null,
    @Json(name = "account_details")   val accountDetails: String? = null,
    @Json(name = "verdicts")          val verdicts: List<String> = emptyList(),
    @Json(name = "blocked")           val blocked: Boolean = false,
    @Json(name = "restricted")        val restricted: Boolean = false,
    @Json(name = "message")           val message: String? = null
) {
    /** Effective integrity level — prefers explicit field over the generic level. */
    val effectiveIntegrityLevel: String get() = integrityLevel ?: level
}

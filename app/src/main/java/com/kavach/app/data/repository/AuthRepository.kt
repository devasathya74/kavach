package com.kavach.app.data.repository

import android.content.Context
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.auth.AdminLoginRequest
import com.kavach.app.data.remote.dto.auth.LoginRequest
import com.kavach.app.data.remote.dto.auth.OtpRequest
import com.kavach.app.data.remote.dto.auth.UserDto
import com.kavach.app.security.AttestationResult
import com.kavach.app.security.IntegrityRepository
import com.kavach.app.utils.BehaviorTracker
import com.kavach.app.utils.DeviceIdUtil
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth repository — OTP login + Device Binding + Token Lifecycle.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  Device Binding Rules                                   │
 * │  ① First login → device registered on server           │
 * │  ② Same device → always allowed                        │
 * │  ③ Different device → 403 device_mismatch              │
 * │     → Hindi error + behaviorTracker.logDeviceMismatch  │
 * │     → Admin must reset via web dashboard               │
 * └─────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  Token Lifecycle                                        │
 * │  ① OTP verify → access_token + refresh_token + expiry  │
 * │  ② TokenAuthenticator handles 401 silently              │
 * │  ③ Refresh fails → clearSession() → force re-login     │
 * └─────────────────────────────────────────────────────────┘
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api                : KavachApiService,
    private val sessionStore       : SessionDataStore,
    private val behaviorTracker    : BehaviorTracker,
    private val integrityRepository: IntegrityRepository,
    @ApplicationContext private val context: Context
) {
    /** Step 1: Request OTP — sends deviceId for pre-check */
    suspend fun requestOtp(pno: String): ApiResult<Unit> = safeApiCall {
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.login(LoginRequest(pno = pno, deviceId = deviceId))
        when {
            resp.isSuccessful -> {
                Timber.d("OTP requested successfully for PNO: $pno")
                ApiResult.Success(Unit)
            }
            resp.code() == 403 -> {
                Timber.e("Device mismatch for PNO: $pno")
                behaviorTracker.logDeviceMismatch(pno)
                ApiResult.Unauthorized("⛔ यह PNO किसी दूसरे डिवाइस पर पंजीकृत है। Admin से संपर्क करें।")
            }
            else -> {
                Timber.e("Login request failed for PNO: $pno, code: ${resp.code()}")
                ApiResult.Error("Login failed: ${resp.code()}", code = resp.code())
            }
        }
    }

    /**
     * Admin Password Login — OTP bypass।
     * केवल is_staff=true officers के लिए।
     * Backend: POST /api/login { pno, password, device_id }
     */
    suspend fun adminPasswordLogin(pno: String, password: String): ApiResult<Unit> = safeApiCall {
        sessionStore.clearSession()
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.adminLogin(AdminLoginRequest(pno = pno, password = password, deviceId = deviceId))
        val body     = resp.body()

        when {
            resp.isSuccessful && body?.token != null && body.user != null -> {
                Timber.d("Admin login success for PNO: $pno")
                sessionStore.saveSession(
                    token        = body.token,
                    refreshToken = body.refreshToken ?: "",
                    expiresIn    = body.expiresIn    ?: 3600,
                    pno          = body.user.pno,
                    name         = body.user.name,
                    rank         = body.user.rank,
                    unit         = body.user.unit,
                    deviceId     = deviceId,
                    deviceSecret = body.user.deviceSecret,
                    role         = body.user.role
                )

                // Admin also gets attested — no exceptions for privilege
                when (val attestation = integrityRepository.attest()) {
                    is AttestationResult.Failed -> {
                        if (com.kavach.app.KavachConfig.PILOT_MODE) {
                            Timber.w("PILOT MODE: Bypassing integrity failure for Admin: $pno")
                            sessionStore.saveIntegrityLevel("DEGRADED")
                        } else {
                            Timber.e("Integrity check failed for Admin: $pno")
                            sessionStore.clearSession()
                            return@safeApiCall ApiResult.Unauthorized(
                                "🛡️ Admin डिवाइस सुरक्षा जाँच विफल।"
                            )
                        }
                    }
                    is AttestationResult.Restricted -> {
                        Timber.w("Admin attestation restricted: $pno")
                        sessionStore.saveIntegrityLevel(attestation.level)
                    }
                    is AttestationResult.Passed -> {
                        Timber.d("Admin attestation passed: $pno")
                        sessionStore.saveIntegrityLevel(attestation.verdict.integrityLevel)
                    }
                    is AttestationResult.Degraded -> {
                        Timber.w("Admin attestation degraded: $pno")
                        sessionStore.saveIntegrityLevel("DEGRADED")
                    }
                }

                syncProfile()
                ApiResult.Success(Unit)
            }
            resp.code() == 401 -> {
                Timber.e("Invalid admin password for PNO: $pno")
                ApiResult.Unauthorized("❌ पासवर्ड गलत है।")
            }
            resp.code() == 404 -> {
                Timber.e("Admin PNO not found: $pno")
                ApiResult.Error("❌ Admin PNO नहीं मिला。", code = 404)
            }
            else -> {
                Timber.e("Admin login failed for PNO: $pno, code: ${resp.code()}")
                ApiResult.Error("Admin login failed: ${resp.code()}", code = resp.code())
            }
        }
    }

    /**
     * Step 2: Verify OTP — runs attestation THEN saves session.
     *
     * Attestation flow:
     * ① OTP verified → JWT issued by backend
     * ② THEN attest device with Play Integrity
     * ③ Save session only if attest passes or is degraded (network only)
     * ④ Hard attest failure (rooted/tampered) → reject login
     */
    suspend fun verifyOtp(pno: String, otp: String): ApiResult<Unit> = safeApiCall {
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.verifyOtp(OtpRequest(pno = pno, otp = otp, deviceId = deviceId))
        val body     = resp.body()

        when {
            resp.isSuccessful && body?.token != null && body.user != null -> {
                Timber.d("OTP verified successfully for PNO: $pno")
                // Save session first (needed for auth headers in nonce request)
                sessionStore.saveSession(
                    token        = body.token,
                    refreshToken = body.refreshToken ?: "",
                    expiresIn    = body.expiresIn    ?: 3600,
                    pno          = body.user.pno,
                    name         = body.user.name,
                    rank         = body.user.rank,
                    unit         = body.user.unit,
                    deviceId     = deviceId,
                    deviceSecret = body.user.deviceSecret,
                    role         = body.user.role
                )

                // Run Play Integrity attestation
                when (val attestation = integrityRepository.attest()) {
                    is AttestationResult.Failed -> {
                        // Hard failure (rooted device / tampered APK) — deny login
                        if (com.kavach.app.KavachConfig.PILOT_MODE) {
                            Timber.w("PILOT MODE: Bypassing integrity failure for User: $pno")
                            sessionStore.saveIntegrityLevel("DEGRADED")
                        } else {
                            Timber.e("Integrity check failed for User: $pno")
                            sessionStore.clearSession()
                            return@safeApiCall ApiResult.Unauthorized(
                                "🛡️ डिवाइस सुरक्षा जाँच विफल। KAVACH इस डिवाइस पर नहीं चलाया जा सकता।"
                            )
                        }
                    }
                    is AttestationResult.Restricted -> {
                        Timber.w("User attestation restricted: $pno")
                        sessionStore.saveIntegrityLevel(attestation.level)
                    }
                    is AttestationResult.Passed -> {
                        Timber.d("User attestation passed: $pno")
                        sessionStore.saveIntegrityLevel(attestation.verdict.integrityLevel)
                    }
                    is AttestationResult.Degraded -> {
                        Timber.w("User attestation degraded: $pno")
                        sessionStore.saveIntegrityLevel("DEGRADED")
                    }
                }

                syncProfile()
                ApiResult.Success(Unit)
            }
            resp.code() == 403 -> {
                val errBody = resp.errorBody()?.string()
                if (errBody?.contains("device_mismatch") == true) {
                    Timber.e("Device mismatch during OTP verify for PNO: $pno")
                    behaviorTracker.logDeviceMismatch(pno)
                    ApiResult.Unauthorized("⛔ डिवाइस मेल नहीं खाता। Admin से device reset करवाएं।")
                } else {
                    Timber.e("OTP verification forbidden for PNO: $pno")
                    ApiResult.Unauthorized("OTP सत्यापन विफल (403)")
                }
            }
            else -> {
                Timber.e("OTP verification failed for PNO: $pno, code: ${resp.code()}")
                ApiResult.Error("OTP verification failed: ${resp.code()}", code = resp.code())
            }
        }
    }

    /**
     * Validate on app startup.
     * Returns false if device fingerprint has changed → force logout.
     */
    suspend fun validateDeviceOnStartup(): Boolean {
        val registeredDeviceId = sessionStore.deviceId.firstOrNull() ?: return true
        val currentDeviceId    = DeviceIdUtil.getDeviceId(context)
        val valid              = currentDeviceId == registeredDeviceId
        if (!valid) behaviorTracker.log(BehaviorTracker.Events.DEVICE_MISMATCH)
        return valid
    }

    /** Fetches the full profile for the current authenticated user */
    suspend fun getProfile(): ApiResult<UserDto> = safeApiCall {
        val resp = api.getProfile()
        val body = resp.body()
        if (resp.isSuccessful && body?.data != null) {
            ApiResult.Success(body.data)
        } else {
            ApiResult.Error("Profile fetch failed: ${resp.code()}", code = resp.code())
        }
    }

    suspend fun syncProfile(): ApiResult<Unit> = safeApiCall {
        val response = api.getProfile()
        if (response.isSuccessful && response.body() != null) {
            val user = response.body()?.data
            if (user != null) {
                sessionStore.saveSession(
                    token = sessionStore.token.first() ?: "",
                    refreshToken = sessionStore.refreshToken.first() ?: "",
                    expiresIn = 3600, 
                    pno = user.pno,
                    name = user.name,
                    rank = user.rank,
                    unit = user.unit,
                    deviceId = user.deviceId,
                    deviceSecret = user.deviceSecret,
                    role = user.role
                )
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Profile data is null")
            }
        } else {
            ApiResult.Error("Profile sync failed: ${response.code()}", code = response.code())
        }
    }

    /** Clears all local session data */
    suspend fun logout() = sessionStore.clearSession()
}

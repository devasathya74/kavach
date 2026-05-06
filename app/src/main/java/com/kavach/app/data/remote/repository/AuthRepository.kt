package com.kavach.app.data.remote.repository

import android.content.Context
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.AdminLoginRequest
import com.kavach.app.data.remote.dto.LoginRequest
import com.kavach.app.data.remote.dto.OtpRequest
import com.kavach.app.data.remote.dto.UserDto
import com.kavach.app.security.AttestationResult
import com.kavach.app.security.IntegrityRepository
import com.kavach.app.utils.BehaviorTracker
import com.kavach.app.utils.DeviceIdUtil
import com.kavach.app.utils.Resource
import com.kavach.app.utils.safeCall
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
    suspend fun requestOtp(pno: String): Resource<Unit> = safeCall {
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.login(LoginRequest(pno = pno, deviceId = deviceId))
        when {
            resp.isSuccessful -> Resource.Success(Unit)
            resp.code() == 403 -> {
                behaviorTracker.logDeviceMismatch(pno)
                Resource.Error("⛔ यह PNO किसी दूसरे डिवाइस पर पंजीकृत है। Admin से संपर्क करें।")
            }
            else -> Resource.Error("Login failed: ${resp.code()}")
        }
    }

    /**
     * Admin Password Login — OTP bypass।
     * केवल is_staff=true officers के लिए।
     * Backend: POST /api/login { pno, password, device_id }
     */
    suspend fun adminPasswordLogin(pno: String, password: String): Resource<Unit> = safeCall {
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.adminLogin(AdminLoginRequest(pno = pno, password = password, deviceId = deviceId))
        val body     = resp.body()

        when {
            resp.isSuccessful && body?.token != null && body.user != null -> {
                if (!body.user.isStaff) {
                    Resource.Error("यह account admin नहीं है।")
                } else {
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
                            sessionStore.clearSession()
                            return@safeCall Resource.Error(
                                "🛡️ Admin डिवाइस सुरक्षा जाँच विफल।"
                            )
                        }
                        is AttestationResult.Restricted ->
                            sessionStore.saveIntegrityLevel(attestation.level)
                        is AttestationResult.Passed ->
                            sessionStore.saveIntegrityLevel(attestation.verdict.integrityLevel)
                        is AttestationResult.Degraded ->
                            sessionStore.saveIntegrityLevel("DEGRADED")
                    }

                    syncProfile()
                    Resource.Success(Unit)
                }
            }
            resp.code() == 401 -> Resource.Error("❌ पासवर्ड गलत है।")
            resp.code() == 404 -> Resource.Error("❌ Admin PNO नहीं मिला।")
            else -> Resource.Error("Admin login failed: ${resp.code()}")
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
    suspend fun verifyOtp(pno: String, otp: String): Resource<Unit> = safeCall {
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.verifyOtp(OtpRequest(pno = pno, otp = otp, deviceId = deviceId))
        val body     = resp.body()

        when {
            resp.isSuccessful && body?.token != null && body.user != null -> {
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
                        sessionStore.clearSession()
                        return@safeCall Resource.Error(
                            "🛡️ डिवाइस सुरक्षा जाँच विफल। KAVACH इस डिवाइस पर नहीं चलाया जा सकता।"
                        )
                    }
                    is AttestationResult.Restricted -> {
                        // BASIC_INTEGRITY only — allow login but mark as restricted
                        sessionStore.saveIntegrityLevel(attestation.level)
                    }
                    is AttestationResult.Passed -> {
                        // STRONG or DEVICE integrity — full access
                        sessionStore.saveIntegrityLevel(attestation.verdict.integrityLevel)
                    }
                    is AttestationResult.Degraded -> {
                        // Network unavailable — allow with degraded marker
                        // Backend controls trust window for offline operations
                        sessionStore.saveIntegrityLevel("DEGRADED")
                    }
                }

                syncProfile()
                Resource.Success(Unit)
            }
            resp.code() == 403 -> {
                val errBody = resp.errorBody()?.string()
                if (errBody?.contains("device_mismatch") == true) {
                    behaviorTracker.logDeviceMismatch(pno)
                    Resource.Error("⛔ डिवाइस मेल नहीं खाता। Admin से device reset करवाएं।")
                } else {
                    Resource.Error("OTP सत्यापन विफल")
                }
            }
            else -> Resource.Error("OTP verification failed: ${resp.code()}")
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
    suspend fun getProfile(): Resource<UserDto> = safeCall {
        val resp = api.getProfile()
        val body = resp.body()
        if (resp.isSuccessful && body?.data != null) {
            Resource.Success(body.data)
        } else {
            Resource.Error("Profile fetch failed: ${resp.code()}")
        }
    }

    suspend fun syncProfile(): Resource<Unit> {
        return try {
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
                    Resource.Success(Unit)
                } else {
                    Resource.Error("Profile data is null")
                }
            } else {
                Resource.Error("Profile sync failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }

    /** Clears all local session data */
    suspend fun logout() = sessionStore.clearSession()
}

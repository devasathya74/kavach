package com.kavach.app.data.remote.repository

import android.content.Context
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.AdminLoginRequest
import com.kavach.app.data.remote.dto.LoginRequest
import com.kavach.app.data.remote.dto.OtpRequest
import com.kavach.app.utils.BehaviorTracker
import com.kavach.app.utils.DeviceIdUtil
import com.kavach.app.utils.Resource
import com.kavach.app.utils.safeCall
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val api             : KavachApiService,
    private val sessionStore    : SessionDataStore,
    private val behaviorTracker : BehaviorTracker,
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
                        isStaff      = true
                    )
                    Resource.Success(Unit)
                }
            }
            resp.code() == 401 -> Resource.Error("❌ पासवर्ड गलत है।")
            resp.code() == 404 -> Resource.Error("❌ Admin PNO नहीं मिला।")
            else -> Resource.Error("Admin login failed: ${resp.code()}")
        }
    }

    /** Step 2: Verify OTP — saves full token lifecycle on success */
    suspend fun verifyOtp(pno: String, otp: String): Resource<Unit> = safeCall {
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.verifyOtp(OtpRequest(pno = pno, otp = otp, deviceId = deviceId))
        val body     = resp.body()

        when {
            resp.isSuccessful && body?.token != null && body.user != null -> {
                sessionStore.saveSession(
                    token        = body.token,
                    refreshToken = body.refreshToken ?: "",   // server must send this
                    expiresIn    = body.expiresIn    ?: 3600, // default 1hr if missing
                    pno          = body.user.pno,
                    name         = body.user.name,
                    rank         = body.user.rank,
                    unit         = body.user.unit,
                    deviceId     = deviceId,
                    isStaff      = body.user.isStaff
                )
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

    /** Clears all local session data */
    suspend fun logout() = sessionStore.clearSession()
}

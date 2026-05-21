package com.kavach.app.data.repository

import android.content.Context
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.auth.AdminLoginRequest
import com.kavach.app.data.remote.dto.auth.LoginRequest
import com.kavach.app.data.remote.dto.auth.OtpRequest
import com.kavach.app.data.remote.dto.auth.UserDto

import com.kavach.app.utils.BehaviorTracker
import com.kavach.app.utils.DeviceIdUtil
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.kavach.app.data.local.db.KavachDatabase
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
    private val db                 : KavachDatabase,
    @ApplicationContext private val context: Context
) {
    /** 
     * Login with PNO and Password.
     * Backend: POST /api/v1/login/ { pno, password, device_id }
     */
    suspend fun loginWithPassword(pno: String, password: String): ApiResult<Unit> = safeApiCall {
        sessionStore.clearSession()
        val deviceId = DeviceIdUtil.getDeviceId(context)
        val resp     = api.adminLogin(AdminLoginRequest(pno = pno, password = password, deviceId = deviceId))
        val body     = resp.body()

        when {
            resp.isSuccessful && body?.token != null && body.user != null -> {
                Timber.d("Login success for PNO: $pno, Role: ${body.user.role}")
                sessionStore.saveSession(
                    token        = body.token,
                    refreshToken = body.refreshToken ?: "",
                    expiresIn    = body.expiresIn    ?: 3600L,
                    pno          = body.user.pno,
                    name         = body.user.profile?.name ?: "",
                    rank         = body.user.profile?.rank?.name ?: "",
                    unit         = body.user.profile?.unit?.name ?: "",
                    deviceId     = deviceId,
                    deviceSecret = body.deviceSecret ?: "",
                    role         = body.user.role,
                    rankLevel    = body.user.profile?.rank?.level ?: 0,
                    androidId    = DeviceIdUtil.getAndroidId(context),
                    deviceName   = DeviceIdUtil.getDeviceName()
                )


                
                syncProfile()
                ApiResult.Success(Unit)
            }
            resp.code() == 401 -> ApiResult.Unauthorized("❌ पासवर्ड गलत है।")
            resp.code() == 404 -> ApiResult.Error("❌ PNO नहीं मिला।", code = 404)
            resp.code() == 403 -> ApiResult.Unauthorized("⛔ डिवाइस मेल नहीं खाता। Admin से संपर्क करें।")
            else -> ApiResult.Error("Login failed: ${resp.code()}", code = resp.code())
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
                Timber.d("Admin login success for PNO: $pno, Role: ${body.user.role}")
                sessionStore.saveSession(
                    token        = body.token,
                    refreshToken = body.refreshToken ?: "",
                    expiresIn    = body.expiresIn    ?: 3600L,
                    pno          = body.user.pno,
                    name         = body.user.profile?.name ?: "",
                    rank         = body.user.profile?.rank?.name ?: "",
                    unit         = body.user.profile?.unit?.name ?: "",
                    deviceId     = deviceId,
                    deviceSecret = body.deviceSecret ?: "",
                    role         = body.user.role,
                    androidId    = DeviceIdUtil.getAndroidId(context),
                    deviceName   = DeviceIdUtil.getDeviceName()
                )



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
            resp.code() == 503 -> {
                Timber.e("Server maintenance (503) during admin login")
                ApiResult.Error("🛠️ सर्वर में रखरखाव (Maintenance) चल रहा है। कृपया कुछ देर बाद प्रयास करें।", 503)
            }
            else -> {
                Timber.e("Admin login failed for PNO: $pno, code: ${resp.code()}")
                ApiResult.Error("Admin login failed: ${resp.code()}", code = resp.code())
            }
        }
    }

    // verifyOtp is removed
    /*
    suspend fun verifyOtp(...) { ... }
    */

    /**
     * Validate on app startup.
     * Returns false if device fingerprint has changed → force logout.
     */
    suspend fun validateDeviceOnStartup(): Boolean {
        val registeredDeviceId = sessionStore.deviceId.firstOrNull() ?: return true
        val currentDeviceId    = DeviceIdUtil.getDeviceId(context)
        val valid              = currentDeviceId == registeredDeviceId
        android.util.Log.d("KAVACH_BIND", "STARTUP_CHECK: VALID=$valid, REGISTERED=${registeredDeviceId.take(8)}, CURRENT=${currentDeviceId.take(8)}")
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
        when {
            response.isSuccessful -> {
                val user = response.body()?.data
                if (user != null) {
                    sessionStore.saveSession(
                        token = sessionStore.token.first(),
                        refreshToken = sessionStore.refreshToken.first(),
                        expiresIn    = 3600L, 
                        pno          = user.pno,
                        name         = user.profile?.name ?: "",
                        rank         = user.profile?.rank?.name ?: "",
                        unit         = user.profile?.unit?.name ?: "",
                        deviceId     = sessionStore.deviceId.first(),
                        deviceSecret = sessionStore.deviceSecret.first(),
                        role         = user.role,
                        rankLevel    = user.profile?.rank?.level ?: 0
                    )
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error("Profile data is null")
                }
            }
            response.code() == 401 || response.code() == 403 -> {
                Timber.e("Unauthorized during profile sync: ${response.code()}")
                ApiResult.Unauthorized("सत्र समाप्त कर दिया गया (Session Expired/Revoked)")
            }
            else -> {
                Timber.e("Profile sync failed: ${response.code()}")
                ApiResult.Error("Profile sync failed: ${response.code()}", code = response.code())
            }
        }
    }

    /** Clears all local session data and local cache */
    suspend fun logout() {
        sessionStore.clearSession()
        with(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
    }
}

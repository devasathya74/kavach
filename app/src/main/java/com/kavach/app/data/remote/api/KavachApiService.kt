package com.kavach.app.data.remote.api

import com.kavach.app.data.remote.dto.orders.*
import com.kavach.app.data.remote.dto.auth.*
import com.kavach.app.data.remote.dto.common.*
import com.kavach.app.data.remote.dto.incident.*
import com.kavach.app.data.remote.dto.system.*
import com.kavach.app.data.remote.dto.training.*
import com.kavach.app.data.remote.dto.personnel.*
import com.kavach.app.data.remote.dto.broadcast.*
import retrofit2.Response
import retrofit2.http.*

interface KavachApiService {

    @POST("api/v1/login/")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<Unit>>

    @POST("api/v1/login/")
    suspend fun adminLogin(@Body body: AdminLoginRequest): Response<AuthResponse>

    @POST("api/v1/verify-otp/")
    suspend fun verifyOtp(@Body body: OtpRequest): Response<AuthResponse>

    @GET("api/v1/profile/")
    suspend fun getProfile(): Response<ApiResponse<UserDto>>

    @GET("api/v1/trainings/")
    suspend fun getTrainings(): Response<ApiResponse<List<TrainingDto>>>

    @GET("api/v1/trainings/{id}/")
    suspend fun getTrainingById(@Path("id") id: String): Response<ApiResponse<TrainingDto>>

    @POST("api/v1/training/start/")
    suspend fun startTraining(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    @POST("api/v1/training/complete/")
    suspend fun completeTraining(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    @GET("api/v1/quiz/{trainingId}/")
    suspend fun getQuizQuestions(@Path("trainingId") trainingId: String): Response<ApiResponse<List<QuizQuestionDto>>>

    @POST("api/v1/quiz/submit/")
    suspend fun submitQuiz(@Body body: QuizSubmitRequest): Response<ApiResponse<QuizResultDto>>

    @GET("api/v1/orders/")
    suspend fun getOrders(): Response<ApiResponse<List<OrderDto>>>

    @GET("api/v1/orders/{id}/")
    suspend fun getOrderById(@Path("id") id: String): Response<ApiResponse<OrderDto>>

    @POST("api/v1/orders/acknowledge/")
    suspend fun acknowledgeOrder(@Body body: AcknowledgeRequest): Response<ApiResponse<Unit>>

    @POST("api/v1/behavior/events/")
    suspend fun sendBehaviorEvents(@Body body: BehaviorBatchRequest): Response<ApiResponse<Unit>>

    @POST("api/v1/behavior/score/")
    suspend fun getUserScore(): Response<ApiResponse<UserScoreDto>>

    @POST("api/v1/training/heartbeat/")
    suspend fun sendHeartbeat(@Body body: HeartbeatRequest): Response<ApiResponse<Unit>>

    @POST("api/v1/consent/")
    suspend fun recordConsent(@Body body: ConsentRequest): Response<ApiResponse<Unit>>

    @GET("api/v1/health")
    suspend fun checkApiHealth(): Response<Map<String, String>>

    @GET("api/v1/health/db")
    suspend fun checkDbHealth(): Response<Map<String, String>>

    @GET("api/v1/health/cache")
    suspend fun checkCacheHealth(): Response<Map<String, String>>

    @POST("api/v1/device/change-request/")
    suspend fun requestDeviceChange(@Body body: DeviceChangeRequest): Response<ApiResponse<DeviceChangeResponse>>

    @GET("api/v1/device/change-request/status/")
    suspend fun getDeviceChangeStatus(): Response<ApiResponse<DeviceChangeResponse>>

    @POST("api/v1/quiz/submit/v2/")
    suspend fun submitQuizV2(@Body body: QuizSubmitRequestV2): Response<ApiResponse<QuizResultDto>>

    @GET("api/v1/admin/users/")
    suspend fun getAdminUsers(): Response<ApiResponse<List<AdminOfficerDto>>>

    @POST("api/v1/admin/user/action/")
    suspend fun performAdminAction(@Body body: AdminUserActionDto): Response<ApiResponse<Unit>>

    @GET("api/v1/admin/suspicious/")
    suspend fun getSuspiciousUsers(): Response<ApiResponse<List<SuspiciousSessionDto>>>

    @GET("api/v1/app-version/")
    suspend fun checkAppVersion(@Query("version_code") versionCode: Int): Response<UpdateInfoDto>

    @POST("api/v1/app-update/log/")
    suspend fun logUpdateEvent(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ApiResponse<Unit>>

    @POST("api/v1/auth/register-fcm/")
    suspend fun registerFcmToken(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    @POST("api/v1/auth/notification-ack/")
    suspend fun sendNotificationAck(@Body body: Map<String, String>): Response<ApiResponse<Unit>>
    
    @GET("api/v1/admin/live-feed/")
    suspend fun getLiveFeed(): Response<ApiResponse<List<LiveFeedEventDto>>>

    @GET("api/v1/admin/analytics/")
    suspend fun getAnalytics(): Response<ApiResponse<SystemAnalyticsDto>>

    @GET("api/v1/admin/config/")
    suspend fun getRemoteConfig(): Response<ApiResponse<Map<String, Any>>>

    @POST("api/v1/admin/config/")
    suspend fun updateRemoteConfig(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ApiResponse<Unit>>


    // ── Play Integrity API ─────────────────────────────────

    /**
     * Step 1: Fetch a server-bound nonce before requesting integrity token.
     * Backend generates: sha256(userId + deviceId + uuid + timestamp)
     * This nonce is single-use — backend marks it consumed after use.
     */
    @POST("api/v1/auth/integrity/nonce/")
    suspend fun getIntegrityNonce(): Response<ApiResponse<IntegrityNonceResponse>>

    /**
     * Step 2: Send opaque integrity token to backend for Google-side verification.
     * Client NEVER decodes this token.
     * Backend calls: https://playintegrity.googleapis.com/v1/{package}:decodeIntegrityToken
     */
    @POST("api/v1/auth/integrity/verify/")
    suspend fun verifyIntegrityToken(@Body body: IntegrityVerifyRequest): Response<ApiResponse<IntegrityVerdict>>
}

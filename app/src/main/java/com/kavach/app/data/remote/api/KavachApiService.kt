package com.kavach.app.data.remote.api

import com.kavach.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface KavachApiService {

    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<Unit>>

    @POST("login")
    suspend fun adminLogin(@Body body: AdminLoginRequest): Response<AuthResponse>

    @POST("verify-otp")
    suspend fun verifyOtp(@Body body: OtpRequest): Response<AuthResponse>

    @GET("profile")
    suspend fun getProfile(): Response<ApiResponse<UserDto>>

    @GET("trainings")
    suspend fun getTrainings(): Response<ApiResponse<List<TrainingDto>>>

    @GET("trainings/{id}")
    suspend fun getTrainingById(@Path("id") id: Int): Response<ApiResponse<TrainingDto>>

    @POST("training/start")
    suspend fun startTraining(@Body body: Map<String, Int>): Response<ApiResponse<Unit>>

    @POST("training/complete")
    suspend fun completeTraining(@Body body: Map<String, Int>): Response<ApiResponse<Unit>>

    @GET("quiz/{trainingId}")
    suspend fun getQuizQuestions(@Path("trainingId") trainingId: Int): Response<ApiResponse<List<QuizQuestionDto>>>

    @POST("quiz/submit")
    suspend fun submitQuiz(@Body body: QuizSubmitRequest): Response<ApiResponse<QuizResultDto>>

    @GET("orders")
    suspend fun getOrders(): Response<ApiResponse<List<OrderDto>>>

    @GET("orders/{id}")
    suspend fun getOrderById(@Path("id") id: String): Response<ApiResponse<OrderDto>>

    @POST("orders/acknowledge")
    suspend fun acknowledgeOrder(@Body body: AcknowledgeRequest): Response<ApiResponse<Unit>>

    @POST("behavior/events")
    suspend fun sendBehaviorEvents(@Body body: BehaviorBatchRequest): Response<ApiResponse<Unit>>

    @POST("behavior/score")
    suspend fun getUserScore(): Response<ApiResponse<UserScoreDto>>

    @POST("training/heartbeat")
    suspend fun sendHeartbeat(@Body body: HeartbeatRequest): Response<ApiResponse<Unit>>

    @POST("consent")
    suspend fun recordConsent(@Body body: ConsentRequest): Response<ApiResponse<Unit>>

    @POST("device/change-request")
    suspend fun requestDeviceChange(@Body body: DeviceChangeRequest): Response<ApiResponse<DeviceChangeResponse>>

    @GET("device/change-request/status")
    suspend fun getDeviceChangeStatus(): Response<ApiResponse<DeviceChangeResponse>>

    @POST("quiz/submit/v2")
    suspend fun submitQuizV2(@Body body: QuizSubmitRequestV2): Response<ApiResponse<QuizResultDto>>

    @GET("admin/users")
    suspend fun getAdminUsers(): Response<ApiResponse<List<AdminOfficerDto>>>

    @POST("admin/user/action")
    suspend fun performAdminAction(@Body body: AdminUserActionDto): Response<ApiResponse<Unit>>

    @GET("admin/suspicious")
    suspend fun getSuspiciousUsers(): Response<ApiResponse<List<SuspiciousSessionDto>>>

    @GET("app-version")
    suspend fun checkAppVersion(): Response<UpdateInfoDto>

    @POST("app-update/log/")
    suspend fun logUpdateEvent(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ApiResponse<Unit>>

    @POST("auth/register-fcm")
    suspend fun registerFcmToken(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    @POST("auth/notification-ack")
    suspend fun sendNotificationAck(@Body body: Map<String, String>): Response<ApiResponse<Unit>>
}

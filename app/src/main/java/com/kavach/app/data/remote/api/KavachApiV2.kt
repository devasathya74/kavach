package com.kavach.app.data.remote.api

import com.kavach.app.data.remote.dto.common.ApiResponse
import com.kavach.app.data.remote.dto.common.PaginatedResponse
import com.kavach.app.data.remote.dto.system.DraftChangeDto
import com.kavach.app.data.remote.dto.personnel.OfficerDto
import com.kavach.app.data.remote.dto.training.TrainingModuleDto
import com.kavach.app.data.remote.dto.v2.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface KavachApiV2 {
    
    @GET("api/v2/auth/users/")
    suspend fun getUsers(
        @Query("page") page: Int = 1,
        @Query("unit__type") unitType: String? = null,
        @Query("profile__company__code") company: String? = null,
        @Query("profile__platoon__number") platoon: Int? = null,
        @Query("search") search: String? = null
    ): PaginatedResponse<OfficerDto>

    @POST("api/v2/auth/users/")
    suspend fun createUser(@Body data: CreateUserRequest): Response<ApiResponse<OfficerDto>>

    @GET("api/v2/auth/users/{id}/")
    suspend fun getUserDetail(@Path("id") id: String): OfficerDto

    @PATCH("api/v2/auth/users/{id}/")
    suspend fun updateUser(@Path("id") id: String, @Body data: UpdateUserRequest): Response<ApiResponse<OfficerDto>>

    @POST("api/v2/auth/users/{id}/revoke-device/")
    suspend fun revokeDevice(@Path("id") id: String, @Body body: GenericIdRequest): Response<ApiResponse<Unit>>

    @POST("api/v2/auth/users/{id}/deactivate/")
    suspend fun deactivateUser(@Path("id") id: String, @Body body: GenericIdRequest): Response<ApiResponse<Unit>>

    @POST("api/v2/auth/users/{id}/reset-password/")
    suspend fun resetPassword(@Path("id") id: String, @Body body: ResetPasswordRequest): Response<ApiResponse<Unit>>

    @POST("api/v2/auth/users/{id}/toggle-status/")
    suspend fun toggleStatus(@Path("id") id: String): Response<ApiResponse<Unit>>

    @DELETE("api/v2/auth/users/{id}/")
    suspend fun deleteUser(@Path("id") id: String): Response<Unit>

    @GET("api/v2/auth/devices/")
    suspend fun getDevices(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null
    ): PaginatedResponse<OfficerDeviceDto>

    @POST("api/v2/auth/users/{id}/global-logout/")
    suspend fun globalLogout(@Path("id") id: String): Response<ApiResponse<Unit>>

    @GET("api/v2/auth/users/{id}/audit-timeline/")
    suspend fun getAuditTimeline(@Path("id") id: String): List<Map<String, Any>>

    @GET("api/v2/auth/v2/audit-timeline/")
    suspend fun getGlobalAuditTimeline(
        @Query("page") page: Int = 1,
        @Query("severity") severity: String? = null,
        @Query("search") search: String? = null
    ): PaginatedResponse<OfficerActivityDto>

    @GET("api/v2/auth/v2/incidents/")
    suspend fun getIncidents(
        @Query("page") page: Int = 1,
        @Query("status") status: String? = null,
        @Query("severity") severity: String? = null
    ): PaginatedResponse<IncidentDto>

    @POST("api/v2/auth/v2/incidents/")
    suspend fun createIncident(@Body data: CreateIncidentRequest): Response<ApiResponse<IncidentDto>>

    @GET("api/v2/auth/v2/broadcasts/")
    suspend fun getBroadcasts(): List<BroadcastDto>

    @POST("api/v2/auth/v2/broadcasts/")
    suspend fun createBroadcast(@Body data: CreateBroadcastRequest): Response<ApiResponse<BroadcastDto>>
    
    @Multipart
    @POST("api/v2/auth/v2/broadcasts/upload/")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part
    ): Response<UploadAttachmentResponse>

    @POST("api/v2/auth/v2/broadcasts/finalize/")
    suspend fun finalizeBroadcast(@Body data: FinalizeBroadcastRequest): Response<ApiResponse<FinalizeBroadcastResponse>>

    @POST("api/v2/auth/v2/broadcasts/{id}/acknowledge/")
    suspend fun acknowledgeBroadcast(@Path("id") id: String): Response<ApiResponse<Unit>>

    @GET("api/v2/auth/v2/field-data/")
    suspend fun getFieldData(): List<FieldDataDto>

    @Multipart
    @POST("api/v2/auth/v2/field-data/")
    suspend fun uploadFieldData(
        @Part file: MultipartBody.Part,
        @Part("title") title: okhttp3.RequestBody,
        @Part("category") category: okhttp3.RequestBody
    ): Response<ApiResponse<FieldDataDto>>

    @GET("api/v2/auth/v2/ota-updates/latest/")
    suspend fun getLatestUpdate(): OtaUpdateDto

    @GET("api/v2/auth/v2/training/")
    suspend fun getTrainingModules(): List<TrainingModuleDto>

    @POST("api/v2/auth/v2/training/{id}/acknowledge/")
    suspend fun acknowledgeTraining(@Path("id") id: String): Response<ApiResponse<Unit>>

    // --- Governance Pipeline ---
    @GET("api/v2/auth/v2/pending-changes/")
    suspend fun getPendingChanges(): List<DraftChangeDto>

    @POST("api/v2/auth/v2/pending-changes/")
    suspend fun createDraftChange(@Body data: CreateDraftChangeRequest): Response<ApiResponse<DraftChangeDto>>

    @POST("api/v2/auth/v2/pending-changes/{id}/approve/")
    suspend fun approveChange(@Path("id") id: String): Response<ApiResponse<Unit>>

    @POST("api/v2/auth/v2/pending-changes/{id}/reject/")
    suspend fun rejectChange(@Path("id") id: String): Response<ApiResponse<Unit>>

    // --- Order Management ---
    @GET("api/v2/auth/v2/orders/")
    suspend fun getOrders(): Response<PaginatedResponse<OrderDto>>

    @GET("api/v2/auth/v2/orders/{id}/")
    suspend fun getOrderDetail(@Path("id") id: String): Response<ApiResponse<OrderDto>>

    @POST("api/v2/auth/v2/orders/{id}/acknowledge/")
    suspend fun acknowledgeOrder(@Path("id") id: String): Response<ApiResponse<Unit>>
}

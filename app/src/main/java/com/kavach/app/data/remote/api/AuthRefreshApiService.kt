package com.kavach.app.data.remote.api

import com.kavach.app.data.remote.dto.auth.RefreshTokenRequest
import com.kavach.app.data.remote.dto.auth.RefreshTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Separate Retrofit interface for token refresh.
 * Intentionally split from KavachApiService so the auth interceptor
 * does NOT add the (expired) Bearer token to this call.
 */
interface AuthRefreshApiService {

    @POST("api/v1/auth/refresh/")
    suspend fun refreshToken(
        @Body body: RefreshTokenRequest
    ): Response<RefreshTokenResponse>
}

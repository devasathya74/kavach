package com.kavach.app.data.remote.dto.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Standard API Response Wrapper.
 * Hardened for deterministic error handling in the field.
 */
@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "status") val status: String,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "trace") val trace: String? = null
) {
    val isSuccess: Boolean get() = status == "success"
}

@JsonClass(generateAdapter = true)
data class PaginatedResponse<T>(
    @Json(name = "count") val count: Int,
    @Json(name = "next") val next: String?,
    @Json(name = "previous") val previous: String?,
    @Json(name = "results") val results: List<T>
)

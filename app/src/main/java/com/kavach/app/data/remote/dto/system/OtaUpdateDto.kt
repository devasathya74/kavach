package com.kavach.app.data.remote.dto.system

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OtaUpdateDto(
    @Json(name = "version_code")    val versionCode: Int,
    @Json(name = "version_name")    val versionName: String,
    @Json(name = "is_force_update") val isForceUpdate: Boolean = false,
    @Json(name = "download_url")    val downloadUrl: String? = null,
    @Json(name = "release_notes")   val releaseNotes: String? = null,
    @Json(name = "published_at")    val publishedAt: String? = null,
    @Json(name = "channel")         val channel: String = "stable"
)

package com.kavach.app.data.remote.dto.broadcast

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BroadcastDto(
    @Json(name = "id")             val id: String,
    @Json(name = "title")          val title: String,
    @Json(name = "message")        val message: String? = null,
    @Json(name = "content")        val content: String? = null,
    @Json(name = "sender_pno")     val senderPno: String? = null,
    @Json(name = "sender_name")    val senderName: String? = null,
    @Json(name = "priority")       val priority: String = "INFO",
    @Json(name = "image_url")      val imageUrl: String? = null,
    @Json(name = "created_at")     val createdAt: String? = null,
    @Json(name = "acknowledged")   val acknowledged: Boolean = false,
    @Json(name = "recipient_unit") val recipientUnit: String? = null,
    @Json(name = "data")           val data: String? = null
)

package com.kavach.app.data.remote.dto.orders

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "id")             val id: String,
    @Json(name = "title")          val title: String,
    @Json(name = "content_text")   val contentText: String? = null,
    @Json(name = "image_url")      val imageUrl: String? = null,
    @Json(name = "issued_by")      val issuedBy: String? = null,
    @Json(name = "created_at")     val createdAt: String,
    @Json(name = "is_mandatory")   val isMandatory: Boolean = false,
    @Json(name = "is_acknowledged") val isAcknowledged: Boolean = false,
    @Json(name = "priority")       val priority: String = "normal",
    @Json(name = "deadline")       val deadline: String? = null
)

@JsonClass(generateAdapter = true)
data class AcknowledgeRequest(
    @Json(name = "order_id")         val orderId: String,
    @Json(name = "device_id")        val deviceId: String,
    @Json(name = "timestamp")        val timestamp: Long,
    @Json(name = "read_duration")    val readDuration: Long,
    @Json(name = "idempotency_key")  val idempotencyKey: String
)

package com.kavach.app.domain.model

sealed interface OrderStatus {
    object Pending : OrderStatus
    object Acknowledged : OrderStatus
    object Completed : OrderStatus
    object Expired : OrderStatus
}

data class Order(
    val id: String,
    val serverId: String?,
    val title: String,
    val content: String,
    val type: String,
    val status: OrderStatus,
    val issuedBy: String,
    val issuedAt: Long,
    val receivedAt: Long,
    val acknowledgedAt: Long?,
    val isSyncing: Boolean = false,
    val imageUrl: String? = null
) {
    val isAcknowledged: Boolean
        get() = status == OrderStatus.Acknowledged

    val contentText: String
        get() = content

    val createdAt: Long
        get() = issuedAt
}


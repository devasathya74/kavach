package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * OrderEntity — Server-authoritative command orders.
 * Follows the "Operational Truth" pattern.
 */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val localId: String,
    val serverId: String? = null,
    val correlationId: String? = null,
    val title: String,
    val content: String,
    val type: String,               // GENERAL, EMERGENCY, DRILL
    val status: String,             // PENDING, ACKNOWLEDGED, COMPLETED, EXPIRED
    val issuedBy: String,
    val issuedAt: Long,             // Server timestamp
    val receivedAt: Long = System.currentTimeMillis(),
    val acknowledgedAt: Long? = null,
    val expiresAt: Long? = null,
    val isDirty: Boolean = false,    // True if local ACK is pending sync
    val version: Int = 1,
    val sequence: Long = 0
)

/**
 * OrderAcknowledgmentEntity — Queue for offline ACKs.
 */
@Entity(
    tableName = "order_ack_queue",
    indices = [Index(value = ["orderId"], unique = true)]
)
data class OrderAckEntity(
    @PrimaryKey val orderId: String,
    val acknowledgedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

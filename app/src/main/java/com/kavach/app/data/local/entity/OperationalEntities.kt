package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_events")
data class ProcessedEventEntity(
    @PrimaryKey val eventId: String,
    val type: String,
    val processedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_ack_queue")
data class NotificationAckEntity(
    @PrimaryKey val deliveryId: String,
    val eventId: String,
    val acknowledgedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

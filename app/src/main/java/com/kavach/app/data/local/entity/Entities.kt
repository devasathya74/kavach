package com.kavach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trainings")
data class TrainingEntity(
    @PrimaryKey val id          : Int,
    val title       : String,
    val description : String,
    val videoUrl    : String,
    val duration    : Int,
    val isMandatory : Boolean,
    val status      : String     // "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED"
)

@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    @PrimaryKey val id            : Int,
    val trainingId    : Int,
    val question      : String,
    val optionA       : String,
    val optionB       : String,
    val optionC       : String,
    val optionD       : String,
    val correctOption : String
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id             : String,
    val title          : String,
    val contentText    : String?,
    val imageUrl       : String?,
    val issuedBy       : String,
    val createdAt      : Long,
    val isMandatory    : Boolean,
    val isAcknowledged : Boolean,
    val priority       : String = "NORMAL",
    val deadline       : String? = null
)

@Entity(tableName = "pending_ack_queue")
data class PendingAckEntity(
    @PrimaryKey val orderId        : String,
    val idempotencyKey : String,    // UUID — ensures exactly-once delivery on retry
    val deviceId       : String,
    val timestamp      : Long,
    val readDuration   : Long
)

/**
 * Local behavior event log.
 * Events are batched here and flushed to server by BehaviorSyncWorker.
 */
@Entity(tableName = "behavior_events")
data class BehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id          : Int = 0,
    val eventType   : String,                // e.g. "SEEK_ATTEMPT", "APP_BACKGROUND"
    val trainingId  : Int?,
    val timestampMs : Long,
    val metadata    : String = "{}"          // JSON string of extra key-value pairs
)

@Entity(tableName = "pending_navigation")
data class PendingNavigationEntity(
    @PrimaryKey val notifId: String,
    val screen: String,
    val timestamp: Long,
    val priority: Int = 0,        // 1 = CRITICAL, 0 = NORMAL
    val isProcessed: Boolean = false,
    val isProcessing: Boolean = false
)


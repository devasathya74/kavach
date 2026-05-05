package com.kavach.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kavach.app.data.local.dao.*
import com.kavach.app.data.local.entity.*

/**
 * Single Room database for the Kavach app.
 *
 * Version history:
 *  v1 — Initial schema (Training, Quiz, Orders, PendingAck)
 *  v2 — PendingAckEntity gains idempotencyKey; BehaviorEventEntity added
 *
 * Note: fallbackToDestructiveMigration() is acceptable for MVP.
 * Replace with proper Migration objects before production release.
 */
@Database(
    entities = [
        TrainingEntity::class,
        QuizQuestionEntity::class,
        OrderEntity::class,
        PendingAckEntity::class,
        BehaviorEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KavachDatabase : RoomDatabase() {
    abstract fun trainingDao()     : TrainingDao
    abstract fun quizDao()         : QuizDao
    abstract fun orderDao()        : OrderDao
    abstract fun pendingAckDao()   : PendingAckDao
    abstract fun behaviorEventDao(): BehaviorEventDao
}

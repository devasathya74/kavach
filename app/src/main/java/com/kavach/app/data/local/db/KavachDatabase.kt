package com.kavach.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kavach.app.data.local.dao.*
import com.kavach.app.data.local.entity.*

/**
 * Single Room database for the Kavach app.
 *
 * Note: Manual migrations are MANDATORY for production stability.
 * Destruction of local data (fallbackToDestructiveMigration) is strictly PROHIBITED.
 */
@Database(
    entities = [
        TrainingEntity::class,
        QuizQuestionEntity::class,
        OrderEntity::class,
        BehaviorEventEntity::class,
        PendingNavigationEntity::class,
        OfficerCacheEntity::class,
        OfficerProfileCacheEntity::class,
        OfficerDeviceCacheEntity::class,
        ProcessedEventEntity::class,
        NotificationAckEntity::class,
        IncidentEntity::class,
        IncidentAttachmentEntity::class,
        PersonnelActionEntity::class,
        OrderAckEntity::class,
        BroadcastEntity::class,
        BroadcastDeliveryEntity::class,
        BroadcastAttachmentEntity::class,
        BroadcastMutationEntity::class,
        BroadcastDraftEntity::class,
        BroadcastDispatchQueueEntity::class,
        BulkMutationEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class KavachDatabase : RoomDatabase() {
    abstract fun trainingDao()     : TrainingDao
    abstract fun quizDao()         : QuizDao
    abstract fun orderDao()        : OrderDao
    abstract fun behaviorEventDao(): BehaviorEventDao
    abstract fun navigationDao()   : NavigationDao
    abstract fun officerDao()      : OfficerDao
    abstract fun incidentDao()     : IncidentDao
    abstract fun broadcastDao()    : BroadcastDao
}

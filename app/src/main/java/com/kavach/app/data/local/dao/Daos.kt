package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {

    @Query("SELECT * FROM trainings ORDER BY id ASC")
    fun getAllTrainings(): Flow<List<TrainingEntity>>

    @Query("SELECT * FROM trainings WHERE id = :id")
    suspend fun getTrainingById(id: Int): TrainingEntity?

    @Upsert
    suspend fun upsertAll(trainings: List<TrainingEntity>)

    @Query("UPDATE trainings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)
}

@Dao
interface QuizDao {

    @Query("SELECT * FROM quiz_questions WHERE trainingId = :trainingId")
    suspend fun getQuestionsForTraining(trainingId: Int): List<QuizQuestionEntity>

    @Upsert
    suspend fun upsertAll(questions: List<QuizQuestionEntity>)
}

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY id DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: String): OrderEntity?

    @Upsert
    suspend fun upsertAll(orders: List<OrderEntity>)

    @Query("UPDATE orders SET isAcknowledged = 1 WHERE id = :id")
    suspend fun markAcknowledged(id: String)
}

@Dao
interface PendingAckDao {

    @Query("SELECT * FROM pending_ack_queue")
    suspend fun getAll(): List<PendingAckEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingAckEntity)

    @Delete
    suspend fun delete(entity: PendingAckEntity)
}

@Dao
interface BehaviorEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BehaviorEventEntity)

    /** Fetch all buffered events (for batch upload). */
    @Query("SELECT * FROM behavior_events ORDER BY timestampMs ASC")
    suspend fun getAll(): List<BehaviorEventEntity>

    /** Remove successfully synced events. */
    @Query("DELETE FROM behavior_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("""
        DELETE FROM behavior_events
        WHERE id NOT IN (
            SELECT id FROM behavior_events ORDER BY timestampMs DESC LIMIT 500
        )
    """)
    suspend fun trimOldEvents()
}

@Dao
interface NavigationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingNavigationEntity)

    @Query("SELECT * FROM pending_navigation WHERE isProcessed = 0 AND isProcessing = 0 ORDER BY priority DESC, timestamp ASC")
    fun getUnprocessedQueue(): Flow<List<PendingNavigationEntity>>

    @Transaction
    suspend fun acquireIntent(notifId: String): PendingNavigationEntity? {
        val intent = getById(notifId)
        if (intent != null && !intent.isProcessed && !intent.isProcessing) {
            markProcessing(notifId)
            return intent
        }
        return null
    }

    @Query("SELECT * FROM pending_navigation WHERE notifId = :notifId")
    suspend fun getById(notifId: String): PendingNavigationEntity?

    @Query("UPDATE pending_navigation SET isProcessing = 1 WHERE notifId = :notifId")
    suspend fun markProcessing(notifId: String)

    @Query("UPDATE pending_navigation SET isProcessed = 1, isProcessing = 0 WHERE notifId = :notifId")
    suspend fun markProcessed(notifId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pending_navigation WHERE notifId = :notifId)")
    suspend fun isHandled(notifId: String): Boolean

    @Query("DELETE FROM pending_navigation WHERE (isProcessed = 1 OR timestamp < :expiryTime) AND priority = 0")
    suspend fun cleanup(expiryTime: Long)

    /** Ensure we don't drop CRITICAL alerts but cap NORMAL ones to 50 */
    @Transaction
    suspend fun insertWithCapping(entity: PendingNavigationEntity) {
        if (entity.priority == 0) {
            val normalCount = getNormalCount()
            if (normalCount >= 50) {
                deleteOldestNormal()
            }
        }
        insert(entity)
    }

    @Query("SELECT COUNT(*) FROM pending_navigation WHERE priority = 0 AND isProcessed = 0")
    suspend fun getNormalCount(): Int

    @Query("DELETE FROM pending_navigation WHERE notifId IN (SELECT notifId FROM pending_navigation WHERE priority = 0 AND isProcessed = 0 ORDER BY timestamp ASC LIMIT 1)")
    suspend fun deleteOldestNormal()
}

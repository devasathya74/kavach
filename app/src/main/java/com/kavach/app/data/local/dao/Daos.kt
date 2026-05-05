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

    /** Hard cap — keep only last 500 events to avoid unbounded growth on offline devices. */
    @Query("""
        DELETE FROM behavior_events
        WHERE id NOT IN (
            SELECT id FROM behavior_events ORDER BY timestampMs DESC LIMIT 500
        )
    """)
    suspend fun trimOldEvents()
}

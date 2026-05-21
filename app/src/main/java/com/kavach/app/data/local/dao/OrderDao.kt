package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.OrderAckEntity
import com.kavach.app.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY issuedAt DESC")
    fun observeAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE serverId = :serverId")
    suspend fun getOrderByServerId(serverId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE localId = :localId")
    suspend fun getOrderByLocalId(localId: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: OrderEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM order_ack_queue WHERE orderId = :serverId)")
    suspend fun hasPendingAck(serverId: String): Boolean

    @Transaction
    suspend fun reconcileOrder(order: OrderEntity) {
        val existing = getOrderByServerId(order.serverId ?: "")
            ?: getOrderByLocalId(order.localId)
            
        val isAckPending = hasPendingAck(order.serverId ?: "NON_EXISTENT")

        if (existing == null) {
            upsertOrder(order)
        } else {
            // PROTECTION: If we have a local pending ACK, don't let server status revert it to PENDING
            if (isAckPending && order.status == "PENDING") {
                upsertOrder(order.copy(
                    localId = existing.localId,
                    status = "ACKNOWLEDGED",
                    isDirty = true
                ))
            } else {
                upsertOrder(order.copy(localId = existing.localId))
            }
        }
    }

    @Query("UPDATE orders SET status = :status, acknowledgedAt = :ackAt, isDirty = 1 WHERE localId = :localId")
    suspend fun markAsAcknowledged(localId: String, status: String = "ACKNOWLEDGED", ackAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAck(ack: OrderAckEntity)

    @Query("SELECT * FROM order_ack_queue WHERE syncStatus = 'PENDING'")
    suspend fun getPendingAcks(): List<OrderAckEntity>

    @Query("DELETE FROM order_ack_queue WHERE orderId = :orderId")
    suspend fun removeAck(orderId: String)

    @Query("UPDATE orders SET isDirty = 0 WHERE serverId = :serverId")
    suspend fun clearDirtyFlag(serverId: String)
}

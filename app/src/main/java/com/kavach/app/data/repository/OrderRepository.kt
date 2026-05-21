package com.kavach.app.data.repository

import android.content.Context
import com.kavach.app.data.local.dao.OrderDao
import com.kavach.app.data.local.entity.OrderAckEntity
import com.kavach.app.data.local.entity.OrderEntity
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.worker.OrderSyncWorker
import com.kavach.app.domain.model.Order
import com.kavach.app.domain.model.OrderStatus
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import timber.log.Timber
import com.kavach.app.utils.BehaviorTracker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orderDao: OrderDao,
    private val api: KavachApiV2,
    private val behaviorTracker: BehaviorTracker
) {

    fun observeOrders(): Flow<List<Order>> =
        orderDao.observeAllOrders().map { entities ->
            entities.map { it.toDomainModel() }
        }

    private fun isUuid(str: String): Boolean {
        return try {
            UUID.fromString(str)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    suspend fun getOrderById(orderId: String): ApiResult<Order> {
        val entity = if (isUuid(orderId)) {
            orderDao.getOrderByLocalId(orderId)
        } else {
            orderDao.getOrderByServerId(orderId)
        }
        return if (entity != null) {
            ApiResult.Success(entity.toDomainModel())
        } else {
            ApiResult.Error("Order not found")
        }
    }

    suspend fun acknowledgeOrder(localId: String, readDuration: Long = 0L): ApiResult<Unit> {
        val order = if (isUuid(localId)) {
            orderDao.getOrderByLocalId(localId)
        } else {
            orderDao.getOrderByServerId(localId)
        } ?: return ApiResult.Error("Order not found")
        
        // OPTIMISTIC: Update local DB first
        orderDao.markAsAcknowledged(order.localId)
        
        // Compliance/Audit logging via Passive Behavior Tracker (preserves Room schema version safety)
        behaviorTracker.log(
            eventType = "ORDER_ACKNOWLEDGED",
            trainingId = order.serverId ?: order.localId,
            metadata = mapOf("read_duration_ms" to readDuration.toString())
        )

        // Queue for background sync
        order.serverId?.let { serverId ->
            orderDao.enqueueAck(OrderAckEntity(orderId = serverId))
            OrderSyncWorker.schedule(context)
        }
        return ApiResult.Success(Unit)
    }


    suspend fun reconcileFromServer(serverId: String): ApiResult<Unit> = safeApiCall {
        val response = api.getOrderDetail(serverId)
        if (response.isSuccessful && response.body() != null) {
            val dto = response.body()!!.data
            if (dto != null) {
                orderDao.reconcileOrder(dto.toEntity())
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Order data is null")
            }
        } else {
            ApiResult.Error("Order reconciliation failed")
        }
    }

    suspend fun refreshOrders(): ApiResult<Unit> = safeApiCall {
        val response = api.getOrders()
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.results.forEach { dto ->
                orderDao.reconcileOrder(dto.toEntity())
            }
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Failed to refresh orders")
        }
    }

    private fun OrderEntity.toDomainModel() = Order(
        id = localId,
        serverId = serverId,
        title = title,
        content = content,
        type = type,
        status = when (status) {
            "PENDING" -> OrderStatus.Pending
            "ACKNOWLEDGED" -> OrderStatus.Acknowledged
            "COMPLETED" -> OrderStatus.Completed
            "EXPIRED" -> OrderStatus.Expired
            else -> OrderStatus.Pending
        },
        issuedBy = issuedBy,
        issuedAt = issuedAt,
        receivedAt = receivedAt,
        acknowledgedAt = acknowledgedAt,
        isSyncing = isDirty
    )
}

// Extension mappers for DTO (Assuming DTOs exist or need to be created)
fun com.kavach.app.data.remote.dto.v2.OrderDto.toEntity() = OrderEntity(
    localId = UUID.randomUUID().toString(), // Will be reconciled by serverId
    serverId = id,
    title = title,
    content = content,
    type = type,
    status = status,
    issuedBy = issuedBy,
    issuedAt = issuedAt,
    expiresAt = expiresAt
)

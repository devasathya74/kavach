package com.kavach.app.data.repository

import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.local.dao.OrderDao
import com.kavach.app.data.local.dao.PendingAckDao
import com.kavach.app.data.local.entity.OrderEntity
import com.kavach.app.data.local.entity.PendingAckEntity
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.orders.AcknowledgeRequest
import com.kavach.app.domain.model.Order
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val api           : KavachApiService,
    private val orderDao      : OrderDao,
    private val pendingAckDao : PendingAckDao,
    private val sessionDataStore: SessionDataStore
) {
    fun getAllOrders(): Flow<List<Order>> =
        orderDao.getAllOrders().map { list -> list.map { it.toDomain() } }

    suspend fun refreshOrders(): ApiResult<Unit> = safeApiCall {
        val resp = api.getOrders()
        if (resp.isSuccessful) {
            val dtos = resp.body()?.data ?: emptyList()
            dtos.forEach { dto ->
                val existing = orderDao.getOrderById(dto.id)
                val resolvedAck = when {
                    existing?.isAcknowledged == true -> true
                    dto.isAcknowledged               -> true
                    else                             -> false
                }
                orderDao.upsertAll(listOf(
                    OrderEntity(
                        id             = dto.id,
                        title          = dto.title,
                        contentText    = dto.contentText,
                        imageUrl       = dto.imageUrl,
                        issuedBy       = dto.issuedBy ?: "Admin",
                        createdAt      = dto.createdAt?.toLongOrNull() ?: System.currentTimeMillis(),
                        isMandatory    = dto.isMandatory,
                        isAcknowledged = resolvedAck,
                        priority       = dto.priority,
                        deadline       = dto.deadline
                    )
                ))
            }
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Failed to fetch orders: ${resp.code()}", code = resp.code())
        }
    }

    suspend fun getOrderById(id: String): ApiResult<Order> = safeApiCall {
        val entity = orderDao.getOrderById(id)
        if (entity != null) ApiResult.Success(entity.toDomain())
        else ApiResult.Error("Order not found", code = 404)
    }

    suspend fun acknowledgeOrder(orderId: String, readDuration: Long): ApiResult<Unit> = safeApiCall {
        val existing = orderDao.getOrderById(orderId)
        if (existing?.isAcknowledged == true) {
            return@safeApiCall ApiResult.Success(Unit)
        }

        val idempotencyKey = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val deviceId = sessionDataStore.deviceId.firstOrNull() ?: "unknown"

        orderDao.markAcknowledged(orderId)

        val resp = api.acknowledgeOrder(
            AcknowledgeRequest(
                orderId        = orderId,
                deviceId       = deviceId,
                timestamp      = timestamp,
                readDuration   = readDuration,
                idempotencyKey = idempotencyKey
            )
        )
        if (!resp.isSuccessful) {
            pendingAckDao.insert(PendingAckEntity(
                orderId = orderId, 
                idempotencyKey = idempotencyKey,
                deviceId = deviceId,
                timestamp = timestamp,
                readDuration = readDuration
            ))
        }
        ApiResult.Success(Unit)
    }

    suspend fun syncPendingAcknowledgments(): ApiResult<Unit> = safeApiCall {
        val pending = pendingAckDao.getAll()
        for (item in pending) {
            val resp = api.acknowledgeOrder(
                AcknowledgeRequest(
                    orderId        = item.orderId,
                    deviceId       = item.deviceId,
                    timestamp      = item.timestamp,
                    readDuration   = item.readDuration,
                    idempotencyKey = item.idempotencyKey
                )
            )
            if (resp.isSuccessful || resp.code() == 409) {
                pendingAckDao.delete(item)
            }
        }
        ApiResult.Success(Unit)
    }

    private fun OrderEntity.toDomain() = Order(
        id             = id,
        title          = title,
        contentText    = contentText,
        imageUrl       = imageUrl,
        issuedBy       = issuedBy,
        createdAt      = createdAt,
        isMandatory    = isMandatory,
        isAcknowledged = isAcknowledged,
        priority       = priority,
        deadline       = deadline
    )
}

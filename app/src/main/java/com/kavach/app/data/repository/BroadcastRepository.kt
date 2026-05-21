package com.kavach.app.data.repository

import android.content.Context
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.entity.BroadcastAttachmentEntity
import com.kavach.app.data.local.entity.BroadcastDeliveryEntity
import com.kavach.app.data.local.entity.BroadcastDispatchQueueEntity
import com.kavach.app.data.local.entity.BroadcastDraftEntity
import com.kavach.app.data.local.entity.BroadcastEntity
import com.kavach.app.data.local.entity.BroadcastMutationEntity
import com.kavach.app.data.local.entity.BroadcastWithStats
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.BroadcastDto
import com.kavach.app.data.remote.worker.BroadcastSyncWorker
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val broadcastDao: BroadcastDao,
    private val api: KavachApiV2,
    private val sessionDataStore: com.kavach.app.data.local.SessionDataStore
) {

    fun observeBroadcasts(): Flow<List<BroadcastEntity>> = broadcastDao.observeAllBroadcasts()

    fun observeBroadcastsWithStats(): Flow<List<BroadcastWithStats>> = 
        broadcastDao.observeBroadcastsWithStats()

    fun observeDeliveries(broadcastId: String): Flow<List<BroadcastDeliveryEntity>> =
        broadcastDao.observeDeliveries(broadcastId)

    suspend fun markAsRead(broadcastId: String) {
        val userId = sessionDataStore.pno.firstOrNull() ?: return
        
        // OPTIMISTIC: Update local delivery state
        broadcastDao.markAsRead(broadcastId, userId)
        
        // Queue mutation for sync
        broadcastDao.enqueueMutation(
            BroadcastMutationEntity(
                broadcastId = broadcastId,
                actionType = "READ"
            )
        )
        BroadcastSyncWorker.schedule(context)
    }

    suspend fun acknowledgeBroadcast(broadcastId: String) {
        val userId = sessionDataStore.pno.firstOrNull() ?: return
        
        // OPTIMISTIC
        broadcastDao.markAsAcknowledged(broadcastId, userId)
        
        broadcastDao.enqueueMutation(
            BroadcastMutationEntity(
                broadcastId = broadcastId,
                actionType = "ACKNOWLEDGE"
            )
        )
        BroadcastSyncWorker.schedule(context)
    }

    // ── Drafts & Dispatch Queue ────────────────────────────────

    suspend fun saveDraft(draft: BroadcastDraftEntity) {
        broadcastDao.saveDraft(draft)
    }

    suspend fun getDraft(draftId: String): BroadcastDraftEntity? {
        return broadcastDao.getDraft(draftId)
    }
    
    suspend fun deleteDraft(draftId: String) {
        broadcastDao.deleteDraft(draftId)
    }

    suspend fun saveAttachment(attachment: BroadcastAttachmentEntity) {
        broadcastDao.insertAttachment(attachment)
    }

    suspend fun getAttachments(broadcastLocalId: String): List<BroadcastAttachmentEntity> {
        return broadcastDao.getAttachments(broadcastLocalId)
    }

    suspend fun enqueueDispatch(job: BroadcastDispatchQueueEntity) {
        broadcastDao.enqueueDispatch(job)
    }

    /**
     * Reconcile authoritative state from server.
     * Implements "Fanout Isolation" to prevent Broadcast Storm corruption.
     */
    suspend fun reconcileFromServer(serverId: String): ApiResult<Unit> = safeApiCall {
        // In a real scenario, this would fetch full broadcast detail
        // For now, we use a list refresh pattern or targeted fetch
        val response = api.getBroadcasts() // Using existing list fetch for now
        val dto = response.find { it.id == serverId }
        
        if (dto != null) {
            val entity = dto.toEntity()
            broadcastDao.reconcileBroadcast(entity)
            
            // Logic for fanout: Ensure my own delivery record exists
            val myPno = sessionDataStore.pno.firstOrNull()
            if (myPno != null) {
                broadcastDao.insertDeliveryRecord(
                    BroadcastDeliveryEntity(
                        broadcastId = entity.serverId!!,
                        recipientId = myPno,
                        status = if (dto.acknowledged) "ACKNOWLEDGED" else "RECEIVED"
                    )
                )
            }
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Broadcast not found on server")
        }
    }

    suspend fun refreshBroadcasts(): ApiResult<Unit> = safeApiCall {
        val dtos = api.getBroadcasts()
        val myPno = sessionDataStore.pno.firstOrNull()
        
        dtos.forEach { dto ->
            val entity = dto.toEntity()
            broadcastDao.reconcileBroadcast(entity)
            
            if (myPno != null) {
                broadcastDao.insertDeliveryRecord(
                    BroadcastDeliveryEntity(
                        broadcastId = entity.serverId!!,
                        recipientId = myPno,
                        status = if (dto.acknowledged) "ACKNOWLEDGED" else "RECEIVED"
                    )
                )
            }
        }
        ApiResult.Success(Unit)
    }

    private fun BroadcastDto.toEntity() = BroadcastEntity(
        localId = UUID.randomUUID().toString(),
        serverId = id,
        title = title,
        content = message,
        senderId = senderPno ?: "SYSTEM",
        senderName = senderName ?: "System Admin",
        type = "GENERAL",
        priority = priority,
        status = "DELIVERED",
        createdAt = System.currentTimeMillis(), // In real app, parse from createdAt string
        version = 1,
        sequence = 0
    )
}



package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.BroadcastAttachmentEntity
import com.kavach.app.data.local.entity.BroadcastDeliveryEntity
import com.kavach.app.data.local.entity.BroadcastDispatchQueueEntity
import com.kavach.app.data.local.entity.BroadcastDraftEntity
import com.kavach.app.data.local.entity.BroadcastEntity
import com.kavach.app.data.local.entity.BroadcastMutationEntity
import com.kavach.app.data.local.entity.BroadcastWithStats
import kotlinx.coroutines.flow.Flow

@Dao
interface BroadcastDao {

    @Query("SELECT * FROM broadcasts ORDER BY createdAt DESC")
    fun observeAllBroadcasts(): Flow<List<BroadcastEntity>>

    @Query("""
        SELECT 
            b.*,
            (SELECT COUNT(*) FROM broadcast_deliveries WHERE broadcastId = b.serverId OR broadcastId = b.localId) as totalRecipients,
            (SELECT COUNT(*) FROM broadcast_deliveries WHERE (broadcastId = b.serverId OR broadcastId = b.localId) AND status IN ('RECEIVED', 'READ', 'ACKNOWLEDGED')) as deliveredCount,
            (SELECT COUNT(*) FROM broadcast_deliveries WHERE (broadcastId = b.serverId OR broadcastId = b.localId) AND status IN ('READ', 'ACKNOWLEDGED')) as readCount,
            (SELECT COUNT(*) FROM broadcast_deliveries WHERE (broadcastId = b.serverId OR broadcastId = b.localId) AND status = 'ACKNOWLEDGED') as ackCount,
            (SELECT COUNT(*) FROM broadcast_deliveries WHERE (broadcastId = b.serverId OR broadcastId = b.localId) AND status = 'FAILED') as failedCount
        FROM broadcasts b
        ORDER BY b.createdAt DESC
    """)
    fun observeBroadcastsWithStats(): Flow<List<BroadcastWithStats>>

    @Query("SELECT * FROM broadcasts WHERE serverId = :serverId")
    suspend fun getBroadcastByServerId(serverId: String): BroadcastEntity?

    @Query("SELECT * FROM broadcasts WHERE localId = :localId")
    suspend fun getBroadcastByLocalId(localId: String): BroadcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBroadcast(broadcast: BroadcastEntity)

    @Transaction
    suspend fun reconcileBroadcast(broadcast: BroadcastEntity) {
        val existing = getBroadcastByServerId(broadcast.serverId ?: "")
            ?: getBroadcastByLocalId(broadcast.localId)
            
        if (existing == null) {
            upsertBroadcast(broadcast)
        } else {
            // Priority: Higher version or higher sequence wins
            if (broadcast.version > existing.version || broadcast.sequence > existing.sequence) {
                upsertBroadcast(broadcast.copy(localId = existing.localId))
            }
        }
    }

    // ── Delivery States ───────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE) // Ignore duplicates for fanout safety
    suspend fun insertDeliveryRecord(delivery: BroadcastDeliveryEntity)

    @Query("UPDATE broadcast_deliveries SET status = :status, readAt = :time WHERE broadcastId = :broadcastId AND recipientId = :userId")
    suspend fun markAsRead(broadcastId: String, userId: String, status: String = "READ", time: Long = System.currentTimeMillis())

    @Query("UPDATE broadcast_deliveries SET status = :status, acknowledgedAt = :time WHERE broadcastId = :broadcastId AND recipientId = :userId")
    suspend fun markAsAcknowledged(broadcastId: String, userId: String, status: String = "ACKNOWLEDGED", time: Long = System.currentTimeMillis())

    @Query("SELECT * FROM broadcast_deliveries WHERE broadcastId = :broadcastId")
    fun observeDeliveries(broadcastId: String): Flow<List<BroadcastDeliveryEntity>>

    // ── Mutation Queue ────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueMutation(mutation: BroadcastMutationEntity)

    @Query("SELECT * FROM broadcast_mutation_queue WHERE isSynced = 0")
    suspend fun getPendingMutations(): List<BroadcastMutationEntity>

    @Query("DELETE FROM broadcast_mutation_queue WHERE id = :id")
    suspend fun removeMutation(id: Long)

    // ── Attachments ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: BroadcastAttachmentEntity)

    @Query("SELECT * FROM broadcast_attachments WHERE broadcastLocalId = :broadcastLocalId")
    suspend fun getAttachments(broadcastLocalId: String): List<BroadcastAttachmentEntity>
    
    // ── Draft & Queue ─────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: BroadcastDraftEntity)

    @Query("SELECT * FROM broadcast_drafts WHERE draftId = :draftId")
    suspend fun getDraft(draftId: String): BroadcastDraftEntity?
    
    @Query("DELETE FROM broadcast_drafts WHERE draftId = :draftId")
    suspend fun deleteDraft(draftId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueDispatch(job: BroadcastDispatchQueueEntity)

    @Query("SELECT * FROM broadcast_dispatch_queue WHERE status IN ('QUEUED', 'UPLOADING', 'READY_FOR_DISPATCH', 'DISPATCHING') ORDER BY createdAt ASC")
    suspend fun getPendingDispatches(): List<BroadcastDispatchQueueEntity>

    @Query("UPDATE broadcast_dispatch_queue SET status = :status, errorMessage = :error, lastAttemptAt = :time, retryCount = retryCount + 1 WHERE dispatchId = :dispatchId")
    suspend fun updateDispatchStatus(dispatchId: String, status: String, error: String? = null, time: Long = System.currentTimeMillis())
}

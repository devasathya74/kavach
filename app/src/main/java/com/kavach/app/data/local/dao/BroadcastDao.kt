package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.BroadcastAttachmentEntity
import com.kavach.app.data.local.entity.BroadcastDeliveryEntity
import com.kavach.app.data.local.entity.BroadcastDispatchQueueEntity
import com.kavach.app.data.local.entity.BroadcastDraftEntity
import com.kavach.app.data.local.entity.BroadcastDraftRecipientEntity
import com.kavach.app.data.local.entity.BroadcastEntity
import com.kavach.app.data.local.entity.BroadcastMutationEntity
import com.kavach.app.data.local.entity.BroadcastWithStats
import kotlinx.coroutines.flow.Flow

/** Lightweight projection for the unit filter dropdown. */
data class UnitSummary(val unitCode: String, val unitName: String)

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

    // ── Draft Recipients (process-death safe selection) ───────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDraftRecipient(recipient: BroadcastDraftRecipientEntity)

    @Query("SELECT officerId FROM broadcast_draft_recipients WHERE draftId = :draftId")
    suspend fun getDraftRecipientIds(draftId: String): List<String>

    @Query("DELETE FROM broadcast_draft_recipients WHERE draftId = :draftId")
    suspend fun clearDraftRecipients(draftId: String)

    /**
     * Atomically replaces all recipients for a draft.
     * Called on every selection toggle so recipients survive process death.
     */
    @Transaction
    suspend fun setDraftRecipients(draftId: String, officerIds: Set<String>) {
        clearDraftRecipients(draftId)
        officerIds.forEach { id ->
            insertDraftRecipient(BroadcastDraftRecipientEntity(draftId = draftId, officerId = id))
        }
    }

    // ── Filter Queries ────────────────────────────────────────

    /**
     * Returns distinct units from the officer cache for the Unit filter dropdown.
     * Uses index on unitCode for performance.
     */
    @Query("""
        SELECT DISTINCT unitCode, unitName 
        FROM officer_cache 
        WHERE isActive = 1 
        ORDER BY unitName ASC
    """)
    fun observeAvailableUnits(): Flow<List<UnitSummary>>

    /**
     * Returns distinct company names for a given unit.
     * Uses composite index (unitCode, searchableName) for performance — avoids full scan.
     */
    @Query("""
        SELECT DISTINCT p.companyName 
        FROM officer_profile_cache p 
        INNER JOIN officer_cache o ON p.officerId = o.id 
        WHERE o.unitCode = :unitCode 
        AND p.companyName IS NOT NULL 
        AND p.companyName != ''
        ORDER BY p.companyName ASC
    """)
    fun observeCompaniesForUnit(unitCode: String): Flow<List<String>>

    // ── Upload Deduplication ──────────────────────────────────

    /**
     * Returns an already-uploaded attachment with the same SHA-256 checksum.
     * Used by UploadWorker to avoid redundant network uploads.
     */
    @Query("SELECT * FROM broadcast_attachments WHERE checksum = :checksum AND uploadStatus = 'UPLOADED' LIMIT 1")
    suspend fun getAttachmentByChecksum(checksum: String): BroadcastAttachmentEntity?
}

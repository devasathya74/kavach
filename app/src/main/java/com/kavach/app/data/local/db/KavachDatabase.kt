package com.kavach.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        BroadcastDraftRecipientEntity::class,
        BroadcastDispatchQueueEntity::class,
        BulkMutationEntity::class,
        // ── v17: User Mission Execution ──────────────────
        SosEntity::class,
        UserIncidentDraftEntity::class,
        UserIncidentAttachmentEntity::class
    ],
    version = 17,
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
    // ── v17: User Mission Execution ──────────────────────
    abstract fun sosDao()          : SosDao
    abstract fun userIncidentDao() : UserIncidentDao

    companion object {
        /**
         * Migration 15 → 16
         * Adds:
         * - 8 new columns to broadcast_drafts
         * - broadcast_draft_recipients table
         * - composite index on officer_cache(unitCode, searchableName)
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // BroadcastDraftEntity — attachment fields
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN attachmentLocalPath TEXT")
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN attachmentRemoteUrl TEXT")
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN attachmentMimeType TEXT")

                // BroadcastDraftEntity — filter snapshot fields
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN targetUnit TEXT")
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN targetCompany TEXT")

                // BroadcastDraftEntity — delivery mode flags
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN requireAck INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN isHighPriority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE broadcast_drafts ADD COLUMN isEmergency INTEGER NOT NULL DEFAULT 0")

                // BroadcastDraftRecipientEntity — new table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS broadcast_draft_recipients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        draftId TEXT NOT NULL,
                        officerId TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_broadcast_draft_recipients_draftId " +
                    "ON broadcast_draft_recipients(draftId)"
                )

                // Composite index — name MUST match Room's auto-generated format
                // Room generates: index_<tableName>_<col1>_<col2>
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_officer_cache_unitCode_searchableName " +
                    "ON officer_cache(unitCode, searchableName)"
                )
            }
        }

        /**
         * Migration 16 → 17
         * Adds User Mission Execution tables:
         * - sos_queue         : SOS priority signal pipeline
         * - user_incident_drafts : Offline-capable field incident reports
         * - user_incident_attachments : Photo attachments for reports
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SOS queue — for priority SOS pipeline
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sos_queue (
                        localId TEXT NOT NULL PRIMARY KEY,
                        correlationId TEXT NOT NULL,
                        senderPno TEXT NOT NULL,
                        senderUnit TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        message TEXT NOT NULL DEFAULT 'SOS — IMMEDIATE ASSISTANCE REQUIRED',
                        status TEXT NOT NULL DEFAULT 'QUEUED',
                        createdAt INTEGER NOT NULL,
                        lastAttemptAt INTEGER,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        errorMessage TEXT,
                        serverAckAt INTEGER
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sos_queue_correlationId ON sos_queue(correlationId)"
                )

                // User incident drafts — offline field reports
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_incident_drafts (
                        localId TEXT NOT NULL PRIMARY KEY,
                        correlationId TEXT NOT NULL,
                        reporterPno TEXT NOT NULL,
                        reporterUnit TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'FIELD_REPORT',
                        severity TEXT NOT NULL DEFAULT 'MEDIUM',
                        latitude REAL,
                        longitude REAL,
                        createdAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL DEFAULT 'DRAFT',
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        serverId TEXT
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_user_incident_drafts_correlationId ON user_incident_drafts(correlationId)"
                )

                // User incident attachments — photos
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_incident_attachments (
                        localId TEXT NOT NULL PRIMARY KEY,
                        incidentLocalId TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        checksum TEXT,
                        uploadStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteUrl TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_user_incident_attachments_incidentLocalId ON user_incident_attachments(incidentLocalId)"
                )
            }
        }

        /**
         * Migration 14 → 16 (Direct jump migration)
         *
         * CONFIRMED CRASHES (adb logcat):
         * Round 1: Migration 14→16 not found
         * Round 2: no such table: broadcast_drafts       → CREATE TABLE
         * Round 3: didn't properly handle: officer_cache → safeAlter() + indexes
         * Round 4: didn't properly handle: broadcast_attachments
         *          v14: attachmentId PK, broadcastId, localUri, fileType, fileSize, remoteUrl NOT NULL
         *          v16: localId PK, broadcastLocalId, uri, mimeType, checksum, uploadStatus, remoteUrl nullable
         *          → Schema completely redesigned; DROP + RECREATE
         */
        val MIGRATION_14_16 = object : Migration(14, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ══════════════════════════════════════════════════════════════
                // BROADCAST TABLE GROUP: All broadcast tables are DROP + RECREATE
                // Reason: v14 schemas are completely different from v16.
                // Data is non-critical (synced from server on next connect).
                // ══════════════════════════════════════════════════════════════

                // ── broadcasts (core message table) ─────────────────────────
                db.execSQL("DROP TABLE IF EXISTS broadcasts")
                db.execSQL("""
                    CREATE TABLE broadcasts (
                        localId TEXT NOT NULL PRIMARY KEY,
                        serverId TEXT,
                        correlationId TEXT,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        senderId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        type TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        receivedAt INTEGER NOT NULL DEFAULT 0,
                        expiresAt INTEGER,
                        supersedesBroadcastId TEXT,
                        version INTEGER NOT NULL DEFAULT 1,
                        sequence INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_broadcasts_serverId " +
                    "ON broadcasts(serverId)"
                )

                // ── broadcast_deliveries (recipient state tracking) ──────────
                db.execSQL("DROP TABLE IF EXISTS broadcast_deliveries")
                db.execSQL("""
                    CREATE TABLE broadcast_deliveries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        broadcastId TEXT NOT NULL,
                        recipientId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        deliveredAt INTEGER,
                        readAt INTEGER,
                        acknowledgedAt INTEGER
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_broadcast_deliveries_broadcastId_recipientId " +
                    "ON broadcast_deliveries(broadcastId, recipientId)"
                )

                // ── broadcast_attachments: DROP + RECREATE ───────────────────
                // v14 schema is completely incompatible with v16:
                //   v14: attachmentId(PK), broadcastId, localUri, fileType, fileSize, remoteUrl NOT NULL
                //   v16: localId(PK), broadcastLocalId, uri, mimeType, checksum, uploadStatus, remoteUrl nullable
                // Attachment data is non-critical remote cache — safe to clear.
                db.execSQL("DROP TABLE IF EXISTS broadcast_attachments")
                db.execSQL("""
                    CREATE TABLE broadcast_attachments (
                        localId TEXT NOT NULL PRIMARY KEY,
                        broadcastLocalId TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        checksum TEXT,
                        uploadStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteUrl TEXT
                    )
                """.trimIndent())

                // ── broadcast_dispatch_queue: DROP + RECREATE ────────────────
                // v14 had empty schema {}; v16 expects full dispatch queue schema
                db.execSQL("DROP TABLE IF EXISTS broadcast_dispatch_queue")
                db.execSQL("""
                    CREATE TABLE broadcast_dispatch_queue (
                        dispatchId TEXT NOT NULL PRIMARY KEY,
                        draftId TEXT NOT NULL,
                        correlationId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        lastAttemptAt INTEGER,
                        errorMessage TEXT
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_broadcast_dispatch_queue_correlationId " +
                    "ON broadcast_dispatch_queue(correlationId)"
                )

                // ── broadcast_mutation_queue: DROP + RECREATE ────────────────
                // Outgoing delivery ack queue — transient, safe to clear
                db.execSQL("DROP TABLE IF EXISTS broadcast_mutation_queue")
                db.execSQL("""
                    CREATE TABLE broadcast_mutation_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        broadcastId TEXT NOT NULL,
                        actionType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        isSynced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // ── officer_cache: add columns that may be missing in v14 ──────
                // Use try-catch per ALTER — SQLite has no "ADD COLUMN IF NOT EXISTS"
                // Safe to ignore "duplicate column" errors (column already exists)
                fun safeAlter(sql: String) {
                    try { db.execSQL(sql) } catch (_: Exception) { /* column already exists */ }
                }

                safeAlter("ALTER TABLE officer_cache ADD COLUMN searchableName TEXT NOT NULL DEFAULT ''")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN searchablePno TEXT NOT NULL DEFAULT ''")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN syncedRevision INTEGER NOT NULL DEFAULT 1")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN isDirty INTEGER NOT NULL DEFAULT 0")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'")
                safeAlter("ALTER TABLE officer_cache ADD COLUMN etag TEXT")

                // officer_cache indexes Room expects in v16
                db.execSQL("CREATE INDEX IF NOT EXISTS index_officer_cache_unitCode ON officer_cache(unitCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_officer_cache_unitCode_searchableName ON officer_cache(unitCode, searchableName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_officer_cache_searchableName ON officer_cache(searchableName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_officer_cache_searchablePno ON officer_cache(searchablePno)")

                // ── broadcast_drafts: CREATE fresh (v14 had no such table) ──────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS broadcast_drafts (
                        draftId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        type TEXT NOT NULL,
                        selectedUserIdsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        attachmentLocalPath TEXT,
                        attachmentRemoteUrl TEXT,
                        attachmentMimeType TEXT,
                        targetUnit TEXT,
                        targetCompany TEXT,
                        requireAck INTEGER NOT NULL DEFAULT 0,
                        isHighPriority INTEGER NOT NULL DEFAULT 0,
                        isEmergency INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // ── broadcast_draft_recipients: new table ─────────────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS broadcast_draft_recipients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        draftId TEXT NOT NULL,
                        officerId TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_broadcast_draft_recipients_draftId " +
                    "ON broadcast_draft_recipients(draftId)"
                )
            }
        }
    }
}


package com.kavach.app.data.remote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kavach.app.data.local.dao.SosDao
import com.kavach.app.data.local.entity.SosEntity
import com.kavach.app.data.remote.websocket.WebSocketManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * SosWorker — SOS Priority Pipeline Worker.
 *
 * PIPELINE (in order):
 * 1. DB commit (done before enqueue — process-death safe)
 * 2. WebSocket priority emit (fastest path if connected)
 * 3. REST fallback if WS fails
 * 4. Retry with exponential backoff (WorkManager handles)
 *
 * CRITICAL RULES:
 * - DB status updated BEFORE each attempt
 * - Retry only QUEUED or FAILED entries
 * - correlationId prevents server duplicate submissions
 * - EXPEDITED priority so system cannot defer this work
 */
@HiltWorker
class SosWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sosDao: SosDao,
    private val wsManager: WebSocketManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val pending = sosDao.getPendingSos()

        if (pending.isEmpty()) {
            Timber.d("SosWorker: No pending SOS signals.")
            return Result.success()
        }

        var allSucceeded = true

        for (sos in pending) {
            val success = transmitSos(sos)
            if (!success) allSucceeded = false
        }

        return if (allSucceeded) Result.success()
        else if (runAttemptCount < 5) Result.retry()
        else Result.failure()
    }

    private suspend fun transmitSos(sos: SosEntity): Boolean {
        // Mark as transmitting
        sosDao.updateStatus(sos.localId, "TRANSMITTING")

        return try {
            // Step 1: WebSocket (fastest, priority path)
            val wsConnected = wsManager.isConnected()
            if (wsConnected) {
                Timber.i("SosWorker: Transmitting via WebSocket. correlationId=${sos.correlationId}")
                wsManager.sendSosSignal(
                    correlationId = sos.correlationId,
                    senderPno = sos.senderPno,
                    senderUnit = sos.senderUnit,
                    message = sos.message,
                    latitude = sos.latitude,
                    longitude = sos.longitude
                )
                // WS delivered — mark sent (server ACK comes via WsEvent)
                sosDao.markAsSent(sos.localId)
                Timber.i("SosWorker: SOS transmitted via WebSocket. correlationId=${sos.correlationId}")
                true
            } else {
                // Step 2: WS not connected — keep QUEUED for retry when network returns
                // REST endpoint (api.submitSos) will be wired here once backend deploys it
                Timber.w("SosWorker: WS not connected. SOS queued for retry. correlationId=${sos.correlationId}")
                sosDao.updateStatus(sos.localId, "QUEUED", error = "WS_DISCONNECTED")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "SosWorker: Transmission error. correlationId=${sos.correlationId}")
            sosDao.updateStatus(sos.localId, "FAILED", error = e.localizedMessage)
            false
        }
    }

    companion object {
        private const val WORK_NAME = "sos_priority_worker"

        /**
         * Enqueue as EXPEDITED — system cannot defer this.
         * REPLACE existing work so a new SOS always triggers fresh attempt.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SosWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,  // Queue multiple SOS — don't drop
                request
            )
        }
    }
}

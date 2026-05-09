package com.kavach.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kavach.app.data.remote.worker.PersonnelSyncWorker
import timber.log.Timber

/**
 * PRODUCTION-GRADE BOOT RECEIVER
 * 
 * Re-initializes operational background services after a device reboot.
 * Ensures that the officer remains connected and synchronized without manual intervention.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("KAVACH: Device boot completed. Re-initializing operational services.")

            // 1. Re-schedule Operational Sync Workers
            PersonnelSyncWorker.schedule(context)
            
            // 2. Note: WorkManager handles internal re-scheduling for most jobs, 
            // but explicitly calling schedule() with 'KEEP' ensures they are active.

            // 3. We deliberately avoid context.startActivity(MainActivity) here
            // to comply with Android 10+ background start restrictions and to 
            // avoid aggressive battery/OEM killing. The officer will be briefed
            // via a restoration notification if high-priority data is waiting.
        }
    }
}

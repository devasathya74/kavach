package com.kavach.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kavach.app.utils.KavachSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KavachApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Schedule unified background sync (acks + behavior events)
        // Uses ExistingPeriodicWorkPolicy.KEEP → safe to call on every launch
        KavachSyncWorker.schedule(this)
    }

    // ── WorkManager + Hilt integration ────────────────────

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

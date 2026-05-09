package com.kavach.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kavach.app.utils.KavachSyncWorker
import com.kavach.app.data.remote.worker.PersonnelSyncWorker
import timber.log.Timber
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KavachApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Schedule unified background sync (acks + behavior events)
        KavachSyncWorker.schedule(this)
        
        // Schedule personnel cache warming
        PersonnelSyncWorker.schedule(this)
    }

    // ── WorkManager + Hilt integration ────────────────────

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

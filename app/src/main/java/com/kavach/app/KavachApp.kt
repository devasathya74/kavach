package com.kavach.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kavach.app.core.perf.PerformanceMonitor
import com.kavach.app.core.sound.SoundManager
import com.kavach.app.data.remote.worker.PersonnelSyncWorker
import com.kavach.app.utils.KavachSyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class KavachApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory      : HiltWorkerFactory
    @Inject lateinit var soundManager       : SoundManager
    @Inject lateinit var performanceMonitor : PerformanceMonitor
    @Inject lateinit var operationalSyncController: com.kavach.app.core.reconciliation.OperationalSyncController

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())

            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        // ── Operational Runtime Startup ─────────────────────────
        // SoundManager: must initialize before any EventBus events fire.
        // Uses @ApplicationContext injected at construction — no argument needed.
        soundManager.initialize()

        // PerformanceMonitor: start before first frame to capture boot behavior.
        // Runs an independent Choreographer loop — does NOT use EventBus.
        if (BuildConfig.DEBUG) {
            performanceMonitor.start()
        }

        // ── Background Workers ──────────────────────────────────
        KavachSyncWorker.schedule(this)
        PersonnelSyncWorker.schedule(this)
        com.kavach.app.data.remote.worker.IncidentSyncWorker.schedule(this)
        com.kavach.app.data.remote.worker.PersonnelMutationWorker.schedule(this)
        com.kavach.app.data.remote.worker.MediaCleanupWorker.schedule(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Forward memory pressure to ResourceAdaptationEngine
        // (injected lazily in di/ — access via EntryPoints if needed)
    }

    // ── WorkManager + Hilt integration ─────────────────────────

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}


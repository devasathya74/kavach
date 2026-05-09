package com.kavach.app.data.local

import android.content.Context
import com.kavach.app.data.local.db.KavachDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class StoragePressureManager(
    private val context: Context,
    private val db: KavachDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun checkAndCleanup() {
        scope.launch {
            val freeSpace = context.cacheDir.freeSpace
            if (freeSpace < 500 * 1024 * 1024) { // < 500MB
                performEmergencyCleanup()
            }
        }
    }

    private suspend fun performEmergencyCleanup() {
        // 1. Purge ancient thumbnails/media cache
        val mediaDir = File(context.filesDir, "evidence")
        if (mediaDir.exists()) {
            mediaDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 7 * 24 * 3600 * 1000) {
                    file.delete()
                }
            }
        }

        // 2. Compact Room DB (Acknowledged alerts, old behavior events)
        // db.behaviorDao().deleteOldEvents(timestamp = System.currentTimeMillis() - 30 * 24 * 3600 * 1000)
    }
}

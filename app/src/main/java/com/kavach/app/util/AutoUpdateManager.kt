package com.kavach.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.kavach.app.BuildConfig
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.UpdateInfoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class AutoUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: KavachApiService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var retryCount = 0
    private val MAX_RETRIES = 3

    suspend fun checkUpdate(): UpdateInfoDto? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.checkAppVersion()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.versionCode > BuildConfig.VERSION_CODE) {
                        return@withContext body
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Update check failed: ${e.message}")
            }
            null
        }
    }

    fun downloadAndInstall(updateInfo: UpdateInfoDto) {
        if (retryCount >= MAX_RETRIES) {
            Log.e("UpdateManager", "Max retries reached.")
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val base = BuildConfig.BASE_URL.removeSuffix("api/").removeSuffix("/")
        val fullUrl = if (updateInfo.downloadUrl.startsWith("http")) {
            updateInfo.downloadUrl
        } else {
            "$base/${updateInfo.downloadUrl.removePrefix("/")}"
        }
        
        val uri = Uri.parse(fullUrl)
        val request = DownloadManager.Request(uri)
            .setTitle("Kavach Elite Update v${updateInfo.versionCode}")
            .setDescription("Downloading encrypted security package...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "kavach_v${updateInfo.versionCode}.apk")
            .addRequestHeader("X-Device-Id", android.os.Build.ID) // Binding verification

        val downloadId = downloadManager.enqueue(request)
        reportEvent("download_started", mapOf("version" to updateInfo.versionCode, "size" to updateInfo.apkSize))

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    processDownload(downloadManager, downloadId, updateInfo)
                    context.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun processDownload(downloadManager: DownloadManager, downloadId: Long, info: UpdateInfoDto) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIdx)) {
                val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUriStr = cursor.getString(localUriIdx)
                val file = File(Uri.parse(localUriStr).path!!)
                
                if (file.length() != info.apkSize) {
                    Log.e("UpdateManager", "Size Mismatch! Expected: ${info.apkSize}, Actual: ${file.length()}")
                    file.delete()
                    retryCount++
                    reportEvent("size_fail", mapOf("expected" to info.apkSize, "actual" to file.length()))
                    return
                }

                if (verifyAndInstall(file, info)) {
                    reportEvent("success", mapOf("version" to info.versionCode))
                } else {
                    retryCount++
                    reportEvent("integrity_fail", mapOf("version" to info.versionCode, "retry" to retryCount))
                }
            } else {
                reportEvent("download_fail", mapOf("version" to info.versionCode))
            }
            cursor.close()
        }
    }

    private fun verifyAndInstall(file: File, info: UpdateInfoDto): Boolean {
        try {
            val actualHash = calculateHash(file)
            if (!actualHash.equals(info.apkHash, ignoreCase = true)) {
                file.delete()
                return false
            }

            if (!isSignatureMatching(file)) {
                file.delete()
                return false
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun reportEvent(event: String, metadata: Map<String, Any>) {
        scope.launch {
            try {
                apiService.logUpdateEvent(mapOf("event" to event, "metadata" to metadata))
            } catch (e: Exception) {
                Log.e("UpdateManager", "Event logging failed: ${e.message}")
            }
        }
    }

    private fun calculateHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val inputStream = FileInputStream(file)
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        inputStream.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isSignatureMatching(file: File): Boolean {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        val currentInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        return info?.signatures?.get(0)?.toCharsString() == currentInfo.signatures?.get(0)?.toCharsString()
    }
}

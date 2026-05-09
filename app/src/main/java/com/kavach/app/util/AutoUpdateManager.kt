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
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.system.UpdateInfoDto
import com.kavach.app.utils.DeviceIdUtil
import com.kavach.app.util.SecurityUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class AutoUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: KavachApiService,
    private val sessionDataStore: SessionDataStore
) {
    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        object Downloading : UpdateStatus()
        object Verifying : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
        data class IncompatibleRollback(val current: Int, val target: Int) : UpdateStatus()
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus = _updateStatus.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var retryCount = 0
    private val MAX_RETRIES = 3

    suspend fun checkUpdate(): UpdateInfoDto? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.checkAppVersion(BuildConfig.VERSION_CODE)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val isNewer = body.versionCode > BuildConfig.VERSION_CODE
                        val isRollback = body.isRollback && body.versionCode < BuildConfig.VERSION_CODE
                        
                        if (isNewer || isRollback) {
                            return@withContext if (com.kavach.app.KavachConfig.PILOT_MODE) {
                                body.copy(
                                    forceUpdate = false,
                                    isCritical = false,
                                    isRollback = false,
                                    minSupportedVersion = 0
                                )
                            } else {
                                body
                            }
                        }
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

        // 1. Check for sufficient storage space
        val availableSpace = getAvailableStorage()
        val requiredSpace = (updateInfo.apkSize ?: 0L) * 2
        if (availableSpace < requiredSpace) { 
            Log.e("UpdateManager", "Storage full! Needed: $requiredSpace, Available: $availableSpace")
            val mbNeeded = (updateInfo.apkSize ?: 0L) / 1024 / 1024 * 2
            _updateStatus.value = UpdateStatus.Error("स्टोरेज भर गया है। कृपया कम से कम $mbNeeded MB खाली करें।")
            reportEvent("storage_full", mapOf<String, Any>("needed" to requiredSpace, "available" to availableSpace))
            return
        }

        _updateStatus.value = UpdateStatus.Downloading

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val base = BuildConfig.BASE_URL.removeSuffix("api/").removeSuffix("/")
        val dlUrl = updateInfo.downloadUrl ?: ""
        val fullUrl = if (dlUrl.startsWith("http")) {
            dlUrl
        } else {
            "$base/${dlUrl.removePrefix("/")}"
        }
        
        val uri = Uri.parse(fullUrl)
        val request = DownloadManager.Request(uri)
            .setTitle("Kavach Elite Update v${updateInfo.versionCode}")
            .setDescription("सुरक्षित अपडेट डाउनलोड किया जा रहा है...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "kavach_v${updateInfo.versionCode}.apk")
            .addRequestHeader("X-Device-Id", DeviceIdUtil.getDeviceId(context)) // Hardened Binding

        val downloadId = downloadManager.enqueue(request)
        reportEvent("download_started", mapOf<String, Any>("version" to updateInfo.versionCode, "size" to (updateInfo.apkSize ?: 0L)))

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
                val publicUri = Uri.parse(localUriStr)
                
                // Hardened Flow: Copy to Private Storage first (TOCTOU protection)
                _updateStatus.value = UpdateStatus.Verifying
                val privateFile = copyToPrivateStorage(publicUri, info.versionCode)
                if (privateFile != null) {
                    if (verifyAndInstall(privateFile, info)) {
                        _updateStatus.value = UpdateStatus.Idle
                        reportEvent("success", mapOf("version" to info.versionCode))
                    } else {
                        // verifyAndInstall already updated status to Error/Blocked
                        retryCount++
                    }
                } else {
                    _updateStatus.value = UpdateStatus.Error("अपडेट फाइल सुरक्षित करने में विफल।")
                    reportEvent("storage_error", mapOf("version" to info.versionCode))
                }
            } else {
                _updateStatus.value = UpdateStatus.Error("डाउनलोड विफल। कृपया इंटरनेट चेक करें।")
                reportEvent("download_fail", mapOf("version" to info.versionCode))
            }
            cursor.close()
        }
    }

    private fun copyToPrivateStorage(publicUri: Uri, versionCode: Int): File? {
        return try {
            val privateFile = File(context.filesDir, "kavach_update_${versionCode}.apk")
            if (privateFile.exists()) privateFile.delete()
            
            context.contentResolver.openInputStream(publicUri)?.use { input ->
                privateFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            privateFile
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to copy to private storage: ${e.message}")
            null
        }
    }

    private fun verifyAndInstall(file: File, info: UpdateInfoDto): Boolean {
        try {
            val fileUri = Uri.fromFile(file)
            val actualHash = calculateHash(fileUri)
            if (!actualHash.equals(info.apkHash, ignoreCase = true)) {
                Log.e("UpdateManager", "HASH MISMATCH! Expected: ${info.apkHash}, Actual: $actualHash")
                _updateStatus.value = UpdateStatus.Error("फाइल करप्ट हो गई है (Hash Mismatch)।")
                reportEvent("integrity_fail", mapOf<String, Any>(
                    "expected" to (info.apkHash ?: ""),
                    "actual" to actualHash,
                    "version" to info.versionCode
                ))
                file.delete()
                return false
            }

            if (!isSignatureMatching(file)) {
                Log.e("UpdateManager", "SIGNATURE MISMATCH! APK is tampered or signed with wrong key.")
                _updateStatus.value = UpdateStatus.Error("अपडेट की सिग्नेचर गलत है। यह सुरक्षित नहीं है।")
                reportEvent("signature_fail", mapOf("version" to info.versionCode))
                file.delete()
                return false
            }

            // 3. Schema Rollback Protection (Hard Block)
            val currentSchema = runCatching { 
                kotlinx.coroutines.runBlocking { sessionDataStore.schemaVersion.first() } 
            }.getOrDefault(1)
            
            if (!com.kavach.app.KavachConfig.PILOT_MODE && info.isRollback && info.schemaVersion < currentSchema) {
                Log.e("UpdateManager", "HARD BLOCK: Rollback to incompatible schema! Current: $currentSchema, Target: ${info.schemaVersion}")
                _updateStatus.value = UpdateStatus.IncompatibleRollback(currentSchema, info.schemaVersion)
                reportEvent("rollback_blocked", mapOf("current" to currentSchema, "target" to info.schemaVersion))
                file.delete()
                return false
            }

            scope.launch {
                sessionDataStore.saveSchemaVersion(info.schemaVersion)
            }

            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e("UpdateManager", "Verification error: ${e.message}")
            file.delete()
            return false
        }
    }

    private fun deleteDownloadedFile(uri: Uri) {
        try {
            val file = File(uri.path ?: "")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to delete corrupted APK: ${e.message}")
        }
    }

    private fun isSignatureMatching(file: File): Boolean {
        val pm = context.packageManager
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.signatures
            }

            val currentPackageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val currentSignatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                currentPackageInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                currentPackageInfo?.signatures
            }

            val newSigHash = signatures?.get(0)?.toCharsString() ?: ""
            val currentSigHash = currentSignatures?.get(0)?.toCharsString() ?: ""

            // Pinning: Hard check against TRUSTED_CERT_HASHES (Key Rotation Support)
            if (SecurityUtils.TRUSTED_CERT_HASHES.isNotEmpty() && SecurityUtils.TRUSTED_CERT_HASHES[0] != "REPLACE_WITH_CURRENT_HASH") {
                return SecurityUtils.TRUSTED_CERT_HASHES.any { it.equals(newSigHash, ignoreCase = true) }
            }

            // Fallback: Must match current app's signature
            newSigHash == currentSigHash
        } catch (e: Exception) {
            false
        }
    }

    private fun getAvailableStorage(): Long {
        val stat = android.os.StatFs(Environment.getDataDirectory().path)
        return stat.availableBytes
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

    private fun calculateHash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        inputStream.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

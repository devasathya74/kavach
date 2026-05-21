package com.kavach.app.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BroadcastFileManager — Manages broadcast attachment files in app-private storage.
 *
 * CRITICAL RULE: content:// URIs must NEVER be stored in DB.
 * Android does not guarantee that a content:// URI granted to the app at pick time
 * will remain valid across process restarts or reboots. This class copies the file
 * into app-private storage immediately on selection so that:
 *  - WorkManager can access the file after process death
 *  - File survives device reboot
 *  - URI permission grant expiry does not cause upload failure
 *
 * Private storage path format:
 *   /data/user/0/<package>/files/broadcast_drafts/<uuid>.<ext>
 */
@Singleton
class BroadcastFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val draftsDir: File = File(context.filesDir, "broadcast_drafts").also {
        if (!it.exists()) it.mkdirs()
    }

    /**
     * Copies a content:// URI to app-private storage.
     *
     * @param uri     The content:// URI obtained from file picker
     * @param mimeType The MIME type of the file (e.g. "image/jpeg")
     * @return Absolute path of the private copy
     * @throws IOException if the URI cannot be read or the copy fails
     */
    suspend fun copyToPrivateStorage(uri: Uri, mimeType: String): String = withContext(Dispatchers.IO) {
        val ext = mimeTypeToExtension(mimeType)
        val destFile = File(draftsDir, "${UUID.randomUUID()}.$ext")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("ContentResolver returned null stream for URI: $uri")

            Timber.d("BroadcastFileManager: Copied to private storage → ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            // Clean up partial file on failure
            if (destFile.exists()) destFile.delete()
            throw IOException("Failed to copy URI to private storage: ${e.message}", e)
        }
    }

    /**
     * Computes a SHA-256 checksum of a file at the given absolute path.
     * Used by UploadWorker for upload deduplication — prevents sending the same file twice.
     *
     * @param absolutePath Absolute path to the file (app-private storage path)
     * @return Hex-encoded SHA-256 hash string
     */
    suspend fun computeChecksum(absolutePath: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val file = File(absolutePath)

        if (!file.exists()) throw IOException("File not found for checksum: $absolutePath")

        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }

        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Deletes a file from app-private storage.
     * Call this after a draft is discarded or broadcast is confirmed sent.
     */
    fun deletePrivateFile(absolutePath: String) {
        val file = File(absolutePath)
        if (file.exists() && file.delete()) {
            Timber.d("BroadcastFileManager: Deleted private file → $absolutePath")
        }
    }

    /**
     * Checks whether a private copy already exists (e.g. ViewModel restored from DB).
     */
    fun fileExists(absolutePath: String): Boolean = File(absolutePath).exists()

    private fun mimeTypeToExtension(mimeType: String): String = when {
        mimeType.contains("png", ignoreCase = true)  -> "png"
        mimeType.contains("jpeg", ignoreCase = true) -> "jpg"
        mimeType.contains("jpg", ignoreCase = true)  -> "jpg"
        mimeType.contains("webp", ignoreCase = true) -> "webp"
        mimeType.contains("pdf", ignoreCase = true)  -> "pdf"
        else -> "bin"
    }
}

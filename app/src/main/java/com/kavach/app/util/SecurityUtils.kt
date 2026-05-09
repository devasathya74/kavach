package com.kavach.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Base64
import java.io.File
import java.security.MessageDigest

object SecurityUtils {

    // ✏️ PILOT CONFIG: Official release key SHA-256 fingerprints.
    // Supports key rotation - any hash in this list is trusted.
    // Extraction: keytool -list -v -keystore release.jks | grep SHA256 (lowercase, no colons)
    val TRUSTED_CERT_HASHES = listOf(
        "6afba54956bf69cac4aefe9c89e65211adc0c335d625fc1a219e3248c8284ab7", // Release
        "f28e695b885fd251be5848671048519b366c037424c22b1531b68c9cd54e17d6"  // Debug
    )
    const val EXPECTED_SIGNATURE_HASH = "6afba54956bf69cac4aefe9c89e65211adc0c335d625fc1a219e3248c8284ab7"

    /**
     * Comprehensive Root Detection
     */
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true
        return false
    }

    /**
     * Enhanced Emulator Detection
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    /**
     * Checks if a debugger is attached
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected()
    }

    /**
     * Runtime Signature Verification (Self-Trust)
     * Verifies that the app has not been tampered with or resigned.
     */
    fun isSignatureValid(context: Context): Boolean {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            for (signature in signatures ?: emptyArray()) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val currentHash = digest.joinToString("") { "%02x".format(it) }
                
                // In production, compare with TRUSTED_CERT_HASHES
                android.util.Log.d("SecurityUtils", "Current Signature Hash: $currentHash")
                
                if (TRUSTED_CERT_HASHES.any { it.equals(currentHash, ignoreCase = true) }) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Final Security Check
     */
    fun isEnvironmentSafe(context: Context): Boolean {
        return !isDeviceRooted() && !isEmulator() && !isDebuggerAttached() && isSignatureValid(context)
    }
}

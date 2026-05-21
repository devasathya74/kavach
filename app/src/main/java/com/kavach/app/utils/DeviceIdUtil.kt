package com.kavach.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * Device ID utility — generates a stable, unique device fingerprint.
 *
 * Uses ANDROID_ID (unique per app-signing key + device since Android 8+)
 * combined with hardware model string for added specificity.
 *
 * The result is SHA-256 hashed so raw ANDROID_ID never leaves the device.
 */
object DeviceIdUtil {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = getAndroidId(context)
        // Hardened Fingerprint: Combine multiple hardware and software markers
        val rawFingerprint = "$androidId|${Build.MODEL}|${Build.MANUFACTURER}|${Build.PRODUCT}|${Build.HARDWARE}|${Build.VERSION.SDK_INT}"
        return sha256(rawFingerprint)
    }

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

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
     * Comprehensive Root Detection - Passive Bypass
     */
    fun isDeviceRooted(): Boolean {
        return false
    }

    /**
     * Enhanced Emulator Detection - Passive Bypass
     */
    fun isEmulator(): Boolean {
        return false
    }

    /**
     * Checks if a debugger is attached - Passive Bypass
     */
    fun isDebuggerAttached(): Boolean {
        return false
    }

    /**
     * Runtime Signature Verification (Self-Trust) - Passive Bypass
     */
    fun isSignatureValid(context: Context): Boolean {
        return true
    }

    /**
     * Final Security Check - Passive Bypass
     */
    fun isEnvironmentSafe(context: Context): Boolean {
        return true
    }
}

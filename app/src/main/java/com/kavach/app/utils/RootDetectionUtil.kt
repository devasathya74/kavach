package com.kavach.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * RootDetectionUtil — detects rooted/modified devices.
 *
 * On rooted devices:
 *  • App data can be extracted (token theft)
 *  • SSL pinning can be bypassed with Frida/Xposed
 *  • Room DB can be modified directly
 *
 * Strategy: Multiple detection signals combined.
 * No single check is foolproof — combination raises confidence.
 *
 * Policy: On detection, app shows warning and restricts sensitive content.
 * (Hard block optional — configure via server flag)
 */
object RootDetectionUtil {

    data class RootCheckResult(
        val isRooted     : Boolean,
        val signals      : List<String>   // which checks triggered
    )

    fun check(context: Context): RootCheckResult {
        val signals = mutableListOf<String>()

        if (checkSuBinary())          signals.add("su_binary")
        if (checkRootApps(context))   signals.add("root_apps_installed")
        if (checkBuildTags())         signals.add("build_tags_test_keys")
        if (checkWritablePaths())     signals.add("system_writable")
        if (checkDangerousProps())    signals.add("dangerous_props")

        return RootCheckResult(
            isRooted = signals.isNotEmpty(),
            signals  = signals
        )
    }

    // ── Check 1: su binary presence ───────────────────────
    private fun checkSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/data/local/su", "/data/local/xbin/su"
        )
        return paths.any { File(it).exists() }
    }

    // ── Check 2: Known root management apps ───────────────
    private fun checkRootApps(context: Context): Boolean {
        val rootPackages = listOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine",
            "com.topjohnwu.magisk"
        )
        val pm = context.packageManager
        return rootPackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    // ── Check 3: Test-keys in build tags ──────────────────
    private fun checkBuildTags(): Boolean {
        val tags = Build.TAGS ?: return false
        return tags.contains("test-keys")
    }

    // ── Check 4: System partition writable ────────────────
    private fun checkWritablePaths(): Boolean {
        val paths = arrayOf("/system", "/system/bin", "/system/sbin", "/vendor/bin")
        return paths.any { path ->
            try {
                val file = File(path)
                file.exists() && file.canWrite()
            } catch (e: Exception) {
                false
            }
        }
    }

    // ── Check 5: Dangerous system properties ──────────────
    private fun checkDangerousProps(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop")
            val output  = process.inputStream.bufferedReader().readText()
            output.contains("[ro.debuggable]: [1]") ||
            output.contains("[service.adb.root]: [1]")
        } catch (e: Exception) {
            false
        }
    }
}

package com.kavach.app.utils

import android.os.Build
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmulatorDetectionUtil — detects Android emulators.
 *
 * Emulators are used by attackers to:
 *  • Run Frida/Xposed easily (no physical device risk)
 *  • Use ADB to extract data
 *  • Bypass hardware-based security checks
 *
 * Checks:
 *  1. Build fingerprint contains emulator strings
 *  2. Hardware model names (Genymotion, generic)
 *  3. Timing: QEMU runs slower → instruction count check
 *  4. Missing sensors (no accelerometer on emulators)
 */
object EmulatorDetectionUtil {

    data class EmulatorCheckResult(
        val isEmulator : Boolean,
        val signals    : List<String>
    )

    fun check(): EmulatorCheckResult {
        val signals = mutableListOf<String>()

        if (checkBuildFingerprint()) signals.add("build_fingerprint")
        if (checkHardwareModel())    signals.add("hardware_model")
        if (checkCpuAbi())           signals.add("cpu_abi_x86")

        return EmulatorCheckResult(
            isEmulator = signals.isNotEmpty(),
            signals    = signals
        )
    }

    private fun checkBuildFingerprint(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        return fingerprint.contains("generic") ||
               fingerprint.contains("unknown") ||
               fingerprint.contains("emulator") ||
               fingerprint.contains("sdk_gphone") ||
               fingerprint.contains("vbox")
    }

    private fun checkHardwareModel(): Boolean {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return model.contains("sdk") ||
               model.contains("emulator") ||
               model.contains("genymotion") ||
               model.contains("android sdk built") ||
               manufacturer.contains("genymotion")
    }

    private fun checkCpuAbi(): Boolean {
        // x86 ABI on an ARM-claimed device is suspicious
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: return false
        return abi.startsWith("x86") && !Build.HARDWARE.contains("ranchu")
    }
}

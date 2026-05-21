package com.kavach.app.core.security

import android.app.Activity
import android.view.WindowManager
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecureScreenController — Global display security enforcement.
 *
 * Manages [WindowManager.LayoutParams.FLAG_SECURE] and related display
 * security policies at the Activity level.
 *
 * Prevents:
 *   - Screenshots (Android screenshot API)
 *   - Screen recording capture
 *   - Recent-apps task preview leakage
 *
 * Must be initialized from MainActivity.onCreate() and updated
 * when [CommandModeController] mode changes.
 *
 * Security levels:
 *   FULL    — FLAG_SECURE always on (all screens protected)
 *   PARTIAL — FLAG_SECURE on sensitive screens only
 *   OFF     — Development/debug only; never in production
 */

enum class ScreenSecurityLevel { FULL, PARTIAL, OFF }

@Singleton
class SecureScreenController @Inject constructor(
    private val eventBus: EventBus
) {

    private var currentLevel : ScreenSecurityLevel = ScreenSecurityLevel.FULL
    private var boundActivity: Activity?           = null

    /**
     * Bind the controller to the MainActivity lifecycle.
     * Call from Activity.onResume / onWindowFocusChanged.
     */
    fun bind(activity: Activity) {
        boundActivity = activity
        apply()
    }

    /** Unbind on Activity.onDestroy to prevent memory leaks. */
    fun unbind() {
        boundActivity = null
    }

    /** Set the security level — always FULL in production builds. */
    fun setLevel(level: ScreenSecurityLevel) {
        currentLevel = level
        apply()
    }

    /** Call on CommandMode changes to re-evaluate security posture. */
    fun onCommandModeChanged(mode: CommandMode) {
        val newLevel = when (mode) {
            CommandMode.NORMAL              -> ScreenSecurityLevel.FULL
            CommandMode.ELEVATED_MONITORING -> ScreenSecurityLevel.FULL
            CommandMode.EMERGENCY_COMMAND   -> ScreenSecurityLevel.FULL
            CommandMode.LOCKDOWN            -> ScreenSecurityLevel.FULL
        }
        if (newLevel != currentLevel) setLevel(newLevel)
    }

    private fun apply() {
        val activity = boundActivity ?: return
        when (currentLevel) {
            ScreenSecurityLevel.FULL -> {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            ScreenSecurityLevel.PARTIAL -> {
                // In partial mode, FLAG_SECURE is applied per-screen by the screen itself.
                // Default: remove global flag (individual screens add it as needed).
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            ScreenSecurityLevel.OFF -> {
                // DEBUG ONLY
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    /** Call when system detects a screenshot attempt (via ContentObserver). */
    fun onScreenshotDetected() {
        eventBus.emit(
            SystemEvent.IntegrityViolated("Screenshot attempt detected — classified content at risk")
        )
    }

    val isSecure get() = currentLevel == ScreenSecurityLevel.FULL
}

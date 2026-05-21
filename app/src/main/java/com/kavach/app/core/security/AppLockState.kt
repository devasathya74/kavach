package com.kavach.app.core.security

/**
 * AppLockState — Centralized lock state for the application.
 */
sealed class AppLockState {
    /** App is locked, requires PIN or Biometrics. */
    object Locked : AppLockState()

    /** App is fully accessible. */
    object Unlocked : AppLockState()

    /** App is locked and biometrics are preferred for unlock. */
    object BiometricRequired : AppLockState()

    /** App is locked and PIN is required (biometric failed or unavailable). */
    object PinRequired : AppLockState()
}

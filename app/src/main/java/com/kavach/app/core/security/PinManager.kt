package com.kavach.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PinManager — Secure local PIN management.
 * 
 * Rules:
 * 1. 6-digit PIN required.
 * 2. Stored locally only (EncryptedSharedPreferences).
 * 3. Hashed (SHA-256) before storage.
 */
@Singleton
class PinManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "kavach_secure_pin",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_PIN_HASH = "hashed_app_pin"
        private const val KEY_PIN_ENABLED = "app_pin_enabled"
    }

    /** Set a new 6-digit PIN. */
    fun setPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        
        val hashed = hashPin(pin)
        sharedPrefs.edit().putString(KEY_PIN_HASH, hashed).apply()
        sharedPrefs.edit().putBoolean(KEY_PIN_ENABLED, true).apply()
        return true
    }

    /** Verify the provided PIN against the stored hash. */
    fun verifyPin(pin: String): Boolean {
        val storedHash = sharedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == storedHash
    }

    /** Clear the PIN. */
    fun clearPin() {
        sharedPrefs.edit().remove(KEY_PIN_HASH).apply()
        sharedPrefs.edit().putBoolean(KEY_PIN_ENABLED, false).apply()
    }

    fun isPinSet(): Boolean {
        return sharedPrefs.getBoolean(KEY_PIN_ENABLED, false) && sharedPrefs.getString(KEY_PIN_HASH, null) != null
    }

    private fun hashPin(pin: String): String {
        val salt = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "kavach_default_salt"
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((pin + salt).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

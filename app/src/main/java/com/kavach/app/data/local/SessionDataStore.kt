package com.kavach.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kavach_prefs")

/**
 * Manages secure token + session persistence using DataStore.
 * Replaces SharedPreferences for a type-safe, coroutine-friendly approach.
 */
@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_TOKEN            = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN    = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRY     = longPreferencesKey("token_expiry_ms")
        private val KEY_PNO              = stringPreferencesKey("pno")
        private val KEY_USER_NAME        = stringPreferencesKey("user_name")
        private val KEY_RANK             = stringPreferencesKey("rank")
        private val KEY_UNIT             = stringPreferencesKey("unit")
        private val KEY_DEVICE_ID        = stringPreferencesKey("registered_device_id")
        private val KEY_DEVICE_SECRET    = stringPreferencesKey("device_secret")
        private val KEY_ROLE             = stringPreferencesKey("user_role")
        // Integrity Attestation
        private val KEY_INTEGRITY_LEVEL  = stringPreferencesKey("integrity_level")
        private val KEY_LAST_ATTESTED_AT = longPreferencesKey("last_attested_at_ms")
        // Consent
        private val KEY_CONSENT_ACCEPTED  = androidx.datastore.preferences.core.booleanPreferencesKey("consent_accepted")
        private val KEY_CONSENT_TIMESTAMP = longPreferencesKey("consent_timestamp_ms")
        private val KEY_PENDING_FORCE_UPDATE = androidx.datastore.preferences.core.booleanPreferencesKey("pending_force_update")
        private val KEY_SCHEMA_VERSION = androidx.datastore.preferences.core.intPreferencesKey("db_schema_version")
        private val KEY_PERMISSIONS_HANDLED = androidx.datastore.preferences.core.booleanPreferencesKey("permissions_handled")
    }

    // Transient State (In-memory only, reset on every app launch)
    private val _isVerifiedInThisSession = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isVerifiedInThisSession = _isVerifiedInThisSession.asStateFlow()

    fun markAsVerified() {
        _isVerifiedInThisSession.value = true
    }

    fun markAsUnverified() {
        _isVerifiedInThisSession.value = false
    }

    val token            : Flow<String?>  = context.dataStore.data.map { it[KEY_TOKEN] }
    val refreshToken     : Flow<String?>  = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val tokenExpiry      : Flow<Long?>    = context.dataStore.data.map { it[KEY_TOKEN_EXPIRY] }
    val pno              : Flow<String?>  = context.dataStore.data.map { it[KEY_PNO] }
    val name             : Flow<String?>  = context.dataStore.data.map { it[KEY_USER_NAME] }
    val rank             : Flow<String?>  = context.dataStore.data.map { it[KEY_RANK] }
    val unit             : Flow<String?>  = context.dataStore.data.map { it[KEY_UNIT] }
    val deviceId         : Flow<String?>  = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val deviceSecret     : Flow<String?>  = context.dataStore.data.map { it[KEY_DEVICE_SECRET] }
    val role             : Flow<String>   = context.dataStore.data.map { it[KEY_ROLE] ?: "" }
    val consentAccepted  : Flow<Boolean>  = context.dataStore.data.map { it[KEY_CONSENT_ACCEPTED] ?: false }
    val consentTimestamp : Flow<Long?>    = context.dataStore.data.map { it[KEY_CONSENT_TIMESTAMP] }
    val pendingForceUpdate: Flow<Boolean> = context.dataStore.data.map { it[KEY_PENDING_FORCE_UPDATE] ?: false }
    val schemaVersion    : Flow<Int>      = context.dataStore.data.map { it[KEY_SCHEMA_VERSION] ?: 1 }

    /**
     * Last known integrity level from Play Integrity API.
     * STRONG | DEVICE | BASIC | DEGRADED | FAILED | (empty = never attested)
     * This is sent as a header to backend on every request.
     */
    val integrityLevel   : Flow<String>   = context.dataStore.data.map { it[KEY_INTEGRITY_LEVEL] ?: "" }

    /**
     * Timestamp of the last successful attestation in milliseconds.
     * Backend uses this to enforce the 30-minute trust window.
     */
    val lastAttestedAt   : Flow<Long>     = context.dataStore.data.map { it[KEY_LAST_ATTESTED_AT] ?: 0L }

    suspend fun saveConsent() {
        context.dataStore.edit { prefs ->
            prefs[KEY_CONSENT_ACCEPTED]  = true
            prefs[KEY_CONSENT_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Saves the integrity level returned by backend after Google-side verification.
     * Also records timestamp for trust-window enforcement.
     */
    suspend fun saveIntegrityLevel(level: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INTEGRITY_LEVEL]  = level
            prefs[KEY_LAST_ATTESTED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun saveSession(
        token        : String,
        refreshToken : String,
        expiresIn    : Long,     // seconds from now
        pno          : String,
        name         : String,
        rank         : String,
        unit         : String,
        deviceId     : String,
        deviceSecret : String,
        role         : String
    ) {
        val expiryMs = System.currentTimeMillis() + expiresIn * 1000
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]         = token
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_TOKEN_EXPIRY]  = expiryMs
            prefs[KEY_PNO]           = pno
            prefs[KEY_USER_NAME]     = name
            prefs[KEY_RANK]          = rank
            prefs[KEY_UNIT]          = unit
            prefs[KEY_DEVICE_ID]     = deviceId
            prefs[KEY_DEVICE_SECRET] = deviceSecret
            prefs[KEY_ROLE]          = role
        }
    }

    /** Called after silent token refresh — only updates token fields. */
    suspend fun updateTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiryMs = System.currentTimeMillis() + expiresIn * 1000
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]         = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_TOKEN_EXPIRY]  = expiryMs
        }
    }

    /** Returns true if the access token is expired or will expire in the next 60 seconds. */
    suspend fun isTokenExpired(): Boolean {
        val expiry = tokenExpiry.firstOrNull() ?: return true
        return System.currentTimeMillis() >= (expiry - 60_000)   // 60s buffer
    }

    /** Persist device ID separately (e.g. after re-verification). */
    suspend fun saveRegisteredDeviceId(deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId
        }
    }

    suspend fun setPendingForceUpdate(pending: Boolean) {
        context.dataStore.edit { it[KEY_PENDING_FORCE_UPDATE] = pending }
    }

    suspend fun saveSchemaVersion(version: Int) {
        context.dataStore.edit { it[KEY_SCHEMA_VERSION] = version }
    }

    val permissionsHandled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PERMISSIONS_HANDLED] ?: false }

    suspend fun savePermissionsHandled() {
        context.dataStore.edit { it[KEY_PERMISSIONS_HANDLED] = true }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRY)
            prefs.remove(KEY_PNO)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_RANK)
            prefs.remove(KEY_UNIT)
            prefs.remove(KEY_ROLE)
            prefs.remove(KEY_INTEGRITY_LEVEL)
            prefs.remove(KEY_LAST_ATTESTED_AT)
            // Note: We deliberately KEEP KEY_CONSENT_ACCEPTED and KEY_DEVICE_ID
            // to maintain legal compliance and device-binding integrity.
        }
        markAsUnverified()
    }
}

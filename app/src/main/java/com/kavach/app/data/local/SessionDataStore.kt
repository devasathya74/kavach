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
        private val KEY_TOKEN         = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRY  = longPreferencesKey("token_expiry_ms")
        private val KEY_PNO           = stringPreferencesKey("pno")
        private val KEY_USER_NAME     = stringPreferencesKey("user_name")
        private val KEY_RANK          = stringPreferencesKey("rank")
        private val KEY_UNIT          = stringPreferencesKey("unit")
        private val KEY_DEVICE_ID     = stringPreferencesKey("registered_device_id")
        private val KEY_IS_STAFF      = androidx.datastore.preferences.core.booleanPreferencesKey("is_staff")
        // Consent
        private val KEY_CONSENT_ACCEPTED  = androidx.datastore.preferences.core.booleanPreferencesKey("consent_accepted")
        private val KEY_CONSENT_TIMESTAMP = longPreferencesKey("consent_timestamp_ms")
        private val KEY_PENDING_FORCE_UPDATE = androidx.datastore.preferences.core.booleanPreferencesKey("pending_force_update")
    }

    val token            : Flow<String?>  = context.dataStore.data.map { it[KEY_TOKEN] }
    val refreshToken     : Flow<String?>  = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val tokenExpiry      : Flow<Long?>    = context.dataStore.data.map { it[KEY_TOKEN_EXPIRY] }
    val pno              : Flow<String?>  = context.dataStore.data.map { it[KEY_PNO] }
    val name             : Flow<String?>  = context.dataStore.data.map { it[KEY_USER_NAME] }
    val rank             : Flow<String?>  = context.dataStore.data.map { it[KEY_RANK] }
    val unit             : Flow<String?>  = context.dataStore.data.map { it[KEY_UNIT] }
    val deviceId         : Flow<String?>  = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val isStaff          : Flow<Boolean>  = context.dataStore.data.map { it[KEY_IS_STAFF] ?: false }
    val consentAccepted  : Flow<Boolean>  = context.dataStore.data.map { it[KEY_CONSENT_ACCEPTED] ?: false }
    val consentTimestamp : Flow<Long?>    = context.dataStore.data.map { it[KEY_CONSENT_TIMESTAMP] }
    val pendingForceUpdate: Flow<Boolean> = context.dataStore.data.map { it[KEY_PENDING_FORCE_UPDATE] ?: false }

    suspend fun saveConsent() {
        context.dataStore.edit { prefs ->
            prefs[KEY_CONSENT_ACCEPTED]  = true
            prefs[KEY_CONSENT_TIMESTAMP] = System.currentTimeMillis()
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
        isStaff      : Boolean
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
            prefs[KEY_IS_STAFF]      = isStaff
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

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}

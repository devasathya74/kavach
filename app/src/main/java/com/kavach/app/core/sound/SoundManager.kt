package com.kavach.app.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.kavach.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SoundManager — Minimal tactical audio engine.
 *
 * Rules (per spec):
 *  - Extremely subtle
 *  - Low frequency / institutional
 *  - No cinematic sci-fi sounds
 *
 * Sounds are loaded once at initialization and played at low volume.
 * Each sound has a specific operational trigger context defined in [TacticalSound].
 *
 * NOTE: Actual .ogg/.mp3 files must be placed in res/raw/. Until then,
 * all play() calls are no-ops (SoundPool returns 0 for unloaded sounds).
 */

enum class TacticalSound {
    UPLINK_CONNECT,      // short click/chime — WebSocket connected
    ALERT_PING,          // low beep — new alert/notification
    ESCALATION_TONE,     // low hum ramp — threat level increase
    THREAT_WARNING,      // triple low beep — WARNING or above
    AUTH_SUCCESS,        // soft confirmation tone
    ACCESS_DENIED,       // short low buzz
    EMERGENCY_BROADCAST, // distinct low tone — emergency broadcast received
}

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<TacticalSound, Int>()

    /** Must be called once at app start (e.g., in Application.onCreate or MainActivity). */
    fun initialize() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()

        // Load sounds from res/raw — file names must match exactly.
        // If files don't exist yet, loadSafe returns 0 (silent no-op).
        soundIds[TacticalSound.UPLINK_CONNECT]       = loadSafe(R.raw.snd_uplink_connect)
        soundIds[TacticalSound.ALERT_PING]           = loadSafe(R.raw.snd_alert_ping)
        soundIds[TacticalSound.ESCALATION_TONE]      = loadSafe(R.raw.snd_escalation)
        soundIds[TacticalSound.THREAT_WARNING]       = loadSafe(R.raw.snd_threat_warning)
        soundIds[TacticalSound.AUTH_SUCCESS]         = loadSafe(R.raw.snd_auth_success)
        soundIds[TacticalSound.ACCESS_DENIED]        = loadSafe(R.raw.snd_access_denied)
        soundIds[TacticalSound.EMERGENCY_BROADCAST]  = loadSafe(R.raw.snd_emergency_broadcast)
    }

    /**
     * Play a tactical sound at [volume] (0.0–1.0).
     * Silent no-op if sound file is missing or pool not initialized.
     */
    fun play(sound: TacticalSound, volume: Float = 0.35f) {
        val pool = soundPool ?: return
        val id   = soundIds[sound] ?: return
        if (id == 0) return

        val clamped = volume.coerceIn(0f, 1f)
        pool.play(id, clamped, clamped, 1, 0, 1.0f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }

    private fun loadSafe(resId: Int): Int = try {
        soundPool?.load(context, resId, 1) ?: 0
    } catch (e: Exception) {
        0 // Resource doesn't exist yet — silent fail
    }
}

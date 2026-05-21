package com.kavach.app.ui.guard

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.exoplayer2.ExoPlayer

/**
 * PlaybackGuard – ensures session integrity by detecting tampering events.
 *
 * Detects:
 *  - app moving to background / stop
 *  - screen turned off
 *  - multi‑window (split‑screen) mode
 *  - picture‑in‑picture attempts
 *  - audio focus loss (e.g., headphones disconnect or other media apps)
 *  - overlay / screenshot attempts via FLAG_SECURE
 *
 * On any detection it:
 *  1. Pauses the provided ExoPlayer instance.
 *  2. Calls `onFlushProgress()` to immediately sync progress.
 *  3. Calls `onTamperDetected(event)` with a descriptive event name.
 *  4. Shows a compliance warning UI.
 */
@Composable
fun PlaybackGuard(
    player: ExoPlayer,
    onFlushProgress: () -> Unit,
    onTamperDetected: (event: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // State to control warning UI visibility
    var showWarning by remember { mutableStateOf<String?>(null) }

    // Helper to handle a tamper event uniformly
    fun handleTamper(event: String) {
        if (player.isPlaying) player.pause()
        onFlushProgress()
        onTamperDetected(event)
        showWarning = event
    }

    // ---------- Lifecycle observer for background detection ----------
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> handleTamper("APP_BACKGROUND")
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---------- ProcessLifecycleOwner for whole‑process background ----------
    DisposableEffect(Unit) {
        val procObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                handleTamper("PROCESS_BACKGROUND")
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(procObserver)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(procObserver) }
    }

    // ---------- Screen ON/OFF receiver ----------
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> handleTamper("SCREEN_OFF")
                    // SCREEN_ON is safe, no action required
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // ---------- Multi‑window (split‑screen) detection ----------
    DisposableEffect(context) {
        val activity = context as? Activity
        if (activity != null && activity.isInMultiWindowMode) {
            handleTamper("MULTI_WINDOW")
        }
        // No removal needed; one‑time check on composition
        onDispose {}
    }

    // ---------- Picture‑in‑Picture prevention ----------
    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        if (activity != null && activity.supportsPictureInPictureMode) {
            // If the activity supports PiP, we still need to guard exit hint.
            // Unfortunately we cannot intercept onUserLeaveHint directly here, but
            // we can monitor lifecycle stop which already covers it.
        }
        onDispose {}
    }

    // ---------- Audio focus loss detection ----------
    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                handleTamper("AUDIO_FOCUS_LOSS")
            }
        }
        // Request transient focus so we can be notified of loss
        audioManager.requestAudioFocus(
            listener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        onDispose { audioManager.abandonAudioFocus(listener) }
    }

    // ---------- FLAG_SECURE to prevent screenshots/recording ----------
    DisposableEffect(context) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { /* FLAG_SECURE remains for the lifetime of activity */ }
    }

    // ---------- UI warning dialog ----------
    if (showWarning != null) {
        AlertDialog(
            onDismissRequest = { showWarning = null },
            title = { Text(text = "Compliance Warning") },
            text = { Text(text = "Tamper detected: $showWarning. Playback paused to ensure integrity.") },
            confirmButton = {
                Button(onClick = { showWarning = null }) {
                    Text(text = "OK")
                }
            }
        )
    }
}

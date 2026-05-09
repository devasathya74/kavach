package com.kavach.app.ui.screens.training

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kavach.app.ui.components.WatermarkOverlay
import com.kavach.app.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Video Player Screen — TRUE lockdown ExoPlayer.
 *
 * Security:
 *  1. `useController = false` — no native seekbar exposed at all
 *  2. Intercept all touch events over the video surface → block seek gestures
 *  3. `seekTo()` guarded in Player.Listener — auto-corrects position if tampered
 *  4. WatermarkOverlay on top with dynamic PNO + timestamp
 *  5. Elapsed-time tracker shown instead of seekbar
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    trainingId      : Int,
    onVideoComplete : () -> Unit,
    viewModel       : VideoPlayerViewModel = hiltViewModel()
) {
    val state   by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Track watched duration for "elapsed / total" display
    var elapsedSeconds  by remember { mutableStateOf(0L) }
    var isPlaying       by remember { mutableStateOf(false) }
    var showBlockToast  by remember { mutableStateOf(false) }

    // ExoPlayer — controller hidden, seek blocked
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {

                // ── 1. Completion trigger ─────────────────
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        viewModel.onVideoCompleted()
                        onVideoComplete()
                    }
                    isPlaying = (playbackState == Player.STATE_READY)
                }

                // ── 2. Anti-seek guard ────────────────────
                // If position jumps forward beyond elapsed by > 3s → snap back
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        val maxAllowed = elapsedSeconds * 1000L
                        if (newPosition.positionMs > maxAllowed + 3000) {
                            // Snap back to the furthest watched position
                            seekTo(maxAllowed)
                            showBlockToast = true
                        }
                    }
                }
            })
        }
    }

    // Elapsed-time ticker (1s interval)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // Reset toast after 2s
    LaunchedEffect(showBlockToast) {
        if (showBlockToast) {
            delay(2000)
            showBlockToast = false
        }
    }

    // Load video
    LaunchedEffect(state.training?.videoUrl) {
        val url = state.training?.videoUrl ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        isPlaying = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                    isPlaying = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Only resume if it was already playing
                    // but we forced it to stop on completion or error
                    // so better to let the user play manually if needed, 
                    // or just auto-play if the state was PLAYING
                    if (state.training != null) {
                        exoPlayer.play()
                        isPlaying = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            // ── Top Bar ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface1)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = state.training?.title ?: "Loading…",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    modifier = Modifier.weight(1f)
                )
                // Elapsed timer display (replaces seekbar)
                Text(
                    text  = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldenYellow
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

        // ── Video + Watermark ─────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = GoldenYellow)
                }
            } else {
                // ── ExoPlayer (no controller, no seekbar) ─
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player          = exoPlayer
                            useController   = false   // ← KEY: completely hide UI controls
                            resizeMode      = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams    = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ── Touch interceptor (blocks seek gestures) ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Allow play/pause on tap only
                                    if (exoPlayer.isPlaying) exoPlayer.pause()
                                    else exoPlayer.play()
                                },
                                onLongPress = { /* block */ },
                                onDoubleTap = { /* block — no double-tap seek */ }
                            )
                        }
                )

                // ── Watermark Overlay ─────────────────────
                WatermarkOverlay(
                    pno       = state.pno
                )

                // ── Seek-blocked toast ────────────────────
                if (showBlockToast) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("⛔ आगे नहीं जा सकते", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ── Progress Bar (view-only, not interactive) ──
        state.training?.let { training ->
            val progress = if (training.duration > 0) {
                (elapsedSeconds.toFloat() / training.duration).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color    = GoldenYellow,
                trackColor = Surface3
            )
        }

        // ── Info ──────────────────────────────────────
        state.training?.let { training ->
            Column(Modifier.padding(20.dp)) {
                Text(training.title, style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                Spacer(Modifier.height(6.dp))
                Text(training.description, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMid)
                Spacer(Modifier.height(12.dp))
                Surface(color = GoldenYellow.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                    Text(
                        text     = "⚠ Video को आगे नहीं किया जा सकता। पूरा देखें — फिर Quiz होगा।",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = GoldenYellow,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

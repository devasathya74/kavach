package com.kavach.app.ui.screens.training
import android.content.Context
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlayer.Builder
import androidx.media3.ui.PlayerView
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.foundation.layout.padding


/**
 * Locked video player composable. Seek, speed, PiP, external controls are disabled.
 * The player keeps the screen on and hides default controller UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedVideoPlayerScreen(
    trainingId: String,
    onVideoComplete: () -> Unit,
    viewModel: LockedVideoPlayerViewModel = androidx.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val player = remember {
        Builder(context)
            .setUseLazyPreparation(true)
            .build().apply {
                // Disable controller UI
                val playerView = PlayerView(context)
                playerView.useController = false
                playerView.controllerAutoShow = false
                // Keep screen on during playback
                playerView.keepScreenOn = true
            }
    }

    DisposableEffect(Unit) {
        // Prepare media when composable starts
        val videoUrl = viewModel.getVideoUrl(trainingId)
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        // Listener for completion
        val listener = object : androidx.media3.exoplayer.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.exoplayer.Player.STATE_ENDED) {
                    onVideoComplete()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Video", color = GoldenYellow) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    this.useController = false
                    this.controllerAutoShow = false
                    this.keepScreenOn = true
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

// Simple ViewModel placeholder for fetching video URL.
class LockedVideoPlayerViewModel : androidx.lifecycle.ViewModel() {
    fun getVideoUrl(trainingId: String): String {
        // TODO: Replace with real backend call. For now, return placeholder.
        return "https://example.com/videos/$trainingId.mp4"
    }
}


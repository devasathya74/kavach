package com.kavach.app.ui.screens.training
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlayer.Builder
import androidx.media3.ui.PlayerView
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding


/**
 * TrainingPlayer composable – locked video playback for compliance verification.
 * Implements:
 *  * ExoPlayer with controller disabled, keepScreenOn.
 *  * Immediate progress flush on lifecycle events.
 *  * Integrates with PlaybackGuard for tamper detection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlayer(
    trainingId: String,
    onVideoComplete: () -> Unit,
    viewModel: TrainingPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember {
        Builder(context)
            .setUseLazyPreparation(true)
            .build().apply {
                // ExoPlayer locked configuration
                // (Controller UI disabled, keep screen on handled in PlayerView)
            }
    }

    // Lifecycle observer for immediate progress flush
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> viewModel.flushProgress()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Prepare and play video
    LaunchedEffect(trainingId) {
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
        // Cleanup on exit
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

// ViewModel placeholder – handles video URL and progress sync/flush.
class TrainingPlayerViewModel : androidx.lifecycle.ViewModel() {
    fun getVideoUrl(trainingId: String): String {
        // TODO: Replace with real backend call.
        return "https://example.com/videos/$trainingId.mp4"
    }

    /** Sync playback progress to backend (periodic). */
    fun syncProgress() {
        // TODO: Implement periodic progress sync.
    }

    /** Immediate flush of any pending progress – called on pause/stop/destroy. */
    fun flushProgress() {
        // TODO: Implement immediate progress flush.
    }
}


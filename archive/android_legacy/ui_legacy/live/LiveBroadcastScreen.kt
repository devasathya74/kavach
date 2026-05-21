package com.kavach.app.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBroadcastScreen(
    onBack: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Disable Back Button during Broadcast
    BackHandler(enabled = true) { }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    viewModel.onStreamError("Interrupted. Retrying...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        prepare()
                        play()
                    }, 3000)
                }
            })
        }
    }

    // Network Monitoring
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while(true) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val isLow = caps?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not() ||
                it.linkDownstreamBandwidthKbps < 1000
            } ?: true
            viewModel.onNetworkStatusChanged(isLow)
            kotlinx.coroutines.delay(5000)
        }
    }

    LaunchedEffect(uiState.currentStreamUrl) {
        uiState.currentStreamUrl?.let { url ->
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } ?: run {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Broadcast") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Video Player Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    if (uiState.isLive && uiState.currentStreamUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Status Overlays
                        Column(
                            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color.Red, MaterialTheme.shapes.extraSmall))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            if (uiState.isLowNetwork) {
                                Text("LOW BANDWIDTH: Audio Mode", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (uiState.isMicEnabled) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .background(Color.Red.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MIC LIVE: SPEAK NOW", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        uiState.error?.let {
                            Text(
                                it,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Waiting for command channel...", color = Color.White)
                        }
                    }
                }

                // Controls Area
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Connection Status: ${if (uiState.isConnected) "Connected" else "Disconnected"}",
                            color = if (uiState.isConnected) Color.Green else Color.Red
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { viewModel.raiseHand() },
                                enabled = !uiState.hasRaisedHand && uiState.isLive,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.BackHand, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.hasRaisedHand) "Hand Raised" else "Raise Hand")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            var showReportDialog by remember { mutableStateOf(false) }

                            OutlinedButton(
                                onClick = { showReportDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Report, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Anonymous Report")
                            }

                            if (showReportDialog) {
                                AnonymousReportDialog(
                                    onDismiss = { showReportDialog = false },
                                    onSubmit = {
                                        viewModel.sendAnonymousReport(it)
                                        showReportDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Order Popup Overlay
            uiState.newOrder?.let { order ->
                OrderOverlayPopup(
                    order = order,
                    onAcknowledge = { viewModel.acknowledgeOrder() }
                )
            }
        }
    }
}

@Composable
fun OrderOverlayPopup(
    order: OrderPopup,
    onAcknowledge: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* No dismiss without ack */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CRITICAL ORDER DIRECTIVE",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = order.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I Acknowledge")
                }
            }
        }
    }
}

@Composable
fun AnonymousReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anonymous Report") },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Enter your message") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (message.isNotBlank()) onSubmit(message) }
            ) {
                Text("Send (Masked)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

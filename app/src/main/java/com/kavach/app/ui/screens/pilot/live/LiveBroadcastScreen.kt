package com.kavach.app.ui.screens.pilot.live

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

    // Disable Back Button during active broadcast
    BackHandler(enabled = uiState.isLive) { }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    viewModel.onStreamError("Stream interrupted. Retrying...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        prepare()
                        play()
                    }, 3000)
                }
            })
        }
    }

    // Network quality monitoring
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while (true) {
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
        onDispose { exoPlayer.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LIVE COMMAND CHANNEL", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isLive) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF051424),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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

                        // Status overlays
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopStart),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, MaterialTheme.shapes.extraSmall)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            if (uiState.isLowNetwork) {
                                Text(
                                    "LOW BANDWIDTH — AUDIO MODE",
                                    color = Color.Yellow,
                                    style = MaterialTheme.typography.labelSmall
                                )
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
                                Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MIC LIVE — SPEAK NOW", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        uiState.error?.let { errMsg ->
                            Text(
                                errMsg,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("AWAITING COMMAND CHANNEL...", color = Color.White)
                        }
                    }
                }

                // Controls
                Surface(
                    color = Color(0xFF0A1B2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "UPLINK: ${if (uiState.isConnected) "CONNECTED" else "DISCONNECTED"}",
                            color = if (uiState.isConnected) Color(0xFF89B59A) else Color(0xFFF4A6A6),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.raiseHand() },
                                enabled = !uiState.hasRaisedHand && uiState.isLive,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF122235))
                            ) {
                                Icon(Icons.Default.BackHand, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.hasRaisedHand) "HAND RAISED" else "RAISE HAND")
                            }

                            var showReportDialog by remember { mutableStateOf(false) }

                            OutlinedButton(
                                onClick = { showReportDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Report, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("REPORT")
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

            // Critical Order Overlay
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
private fun OrderOverlayPopup(
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
            color = Color(0xFF1A0505),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "⚠ CRITICAL DIRECTIVE",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF4A6A6),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(order.content, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4A6A6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ACKNOWLEDGE", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun AnonymousReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ANONYMOUS REPORT", fontWeight = FontWeight.ExtraBold) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Enter message") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (message.isNotBlank()) onSubmit(message) }) {
                Text("SUBMIT (MASKED)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

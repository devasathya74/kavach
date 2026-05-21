package com.kavach.app.ui.screens.user.sync

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.remote.worker.SosWorker
import com.kavach.app.data.remote.worker.UserIncidentSyncWorker
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySyncStatusScreen(
    viewModel: MySyncStatusViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DEVICE STATUS",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlueDarker
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── TOP STATE HERO CARD ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(NavyBlueDarker, NavyBlueDarker.copy(alpha = 0.8f))
                        )
                    )
                    .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pulsating or dynamic status icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(
                                when {
                                    state.totalPendingUploads > 0 -> GoldenYellow.copy(alpha = 0.15f)
                                    state.isOnline -> CyberGreen.copy(alpha = 0.15f)
                                    else -> Color.Gray.copy(alpha = 0.15f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    state.totalPendingUploads > 0 -> GoldenYellow
                                    state.isOnline -> CyberGreen
                                    else -> Color.Gray
                                },
                                RoundedCornerShape(36.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                state.totalPendingUploads > 0 -> Icons.Default.Sync
                                state.isOnline -> Icons.Default.CheckCircle
                                else -> Icons.Default.CloudOff
                            },
                            contentDescription = null,
                            tint = when {
                                state.totalPendingUploads > 0 -> GoldenYellow
                                state.isOnline -> CyberGreen
                                else -> Color.Gray
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            state.totalPendingUploads > 0 -> "PENDING UPLOADS QUEUED"
                            state.isOnline -> "SYSTEM OPERATIONAL"
                            else -> "OFFLINE MODE ACTIVE"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            state.totalPendingUploads > 0 -> "${state.totalPendingUploads} item(s) waiting to upload"
                            state.isOnline -> "All field data synchronized with headquarters"
                            else -> "Incident draft creation remains fully functional"
                        },
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── NETWORK & WEBSOCKET DETAILS ──
            Text(
                "CONNECTION RUNTACK",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            // Connection Info Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyBlueDarker)
                    .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Network Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (state.isOnline) CyberGreen else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Active Network", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (state.isOnline) CyberGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (state.isOnline) "ONLINE" else "OFFLINE",
                            color = if (state.isOnline) CyberGreen else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.1f))

                // Websocket Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = if (state.wsConnected) CyberGreen else GoldenYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("WebSocket Link", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    state.wsConnected -> CyberGreen.copy(alpha = 0.1f)
                                    state.isOnline -> GoldenYellow.copy(alpha = 0.1f)
                                    else -> Color.Gray.copy(alpha = 0.1f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = state.wsStateName,
                            color = when {
                                state.wsConnected -> CyberGreen
                                state.isOnline -> GoldenYellow
                                else -> Color.Gray
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── OFFLINE TRANSMISSION QUEUE ──
            Text(
                "OFFLINE SYNC QUEUE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyBlueDarker)
                    .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SOS Queue row
                QueueRow(
                    imageVector = Icons.Default.Warning,
                    label = "Emergency SOS Alerts",
                    count = state.pendingSosCount,
                    accentColor = DangerRed
                )

                Divider(color = Color.Gray.copy(alpha = 0.1f))

                // Incident draft row
                QueueRow(
                    imageVector = Icons.Default.AssignmentLate,
                    label = "Incident Report Drafts",
                    count = state.pendingIncidentsCount,
                    accentColor = Color(0xFFF87171)
                )

                Divider(color = Color.Gray.copy(alpha = 0.1f))

                // Attachment row
                QueueRow(
                    imageVector = Icons.Default.InsertPhoto,
                    label = "Photo Evidence Files",
                    count = state.pendingAttachmentsCount,
                    accentColor = Color(0xFF60A5FA)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── ACTION BUTTONS ──
            Button(
                onClick = {
                    // Trigger manual resync enqueues
                    SosWorker.schedule(context)
                    UserIncidentSyncWorker.schedule(context)
                },
                enabled = state.isOnline && state.totalPendingUploads > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (state.isOnline && state.totalPendingUploads > 0) NavyBlueDarker else Color.Gray,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "FORCE TRANSMIT QUEUE",
                    color = if (state.isOnline && state.totalPendingUploads > 0) NavyBlueDarker else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun QueueRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (count > 0) accentColor.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f)
                )
                .border(
                    1.dp,
                    if (count > 0) accentColor else Color.Gray.copy(alpha = 0.2f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (count > 0) "$count PENDING" else "CLEARED",
                color = if (count > 0) accentColor else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

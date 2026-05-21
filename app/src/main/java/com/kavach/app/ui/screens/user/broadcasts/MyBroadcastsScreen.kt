package com.kavach.app.ui.screens.user.broadcasts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.local.entity.BroadcastEntity
import com.kavach.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * MyBroadcastsScreen — Received broadcasts.
 *
 * USER RULE: No delivery analytics. No recipient count. No dispatch data.
 * Only: read/acknowledge my broadcasts.
 *
 * FIELD DESIGN:
 * - Emergency broadcasts bold red, full width
 * - One-tap acknowledge
 * - Unread indicator dot
 * - Tab: All / Emergency / Unread
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBroadcastsScreen(
    viewModel: MyBroadcastsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionDone) {
        state.actionDone?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionDone()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MY BROADCASTS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "RECEIVED ORDERS & ALERTS",
                            fontSize = 9.sp,
                            color = GoldenYellow,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GoldenYellow)
                    }
                },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(color = GoldenYellow, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GoldenYellow)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark, titleContentColor = OnSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Bar
            BroadcastTabBar(
                selected = state.selectedTab,
                unreadCount = state.unread.size,
                emergencyCount = state.emergency.size,
                onTabSelected = viewModel::selectTab
            )

            if (state.displayList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = OnSurfaceMid.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (state.selectedTab) {
                                BroadcastViewTab.UNREAD -> "NO UNREAD BROADCASTS"
                                BroadcastViewTab.EMERGENCY -> "NO EMERGENCY BROADCASTS"
                                else -> "NO BROADCASTS YET"
                            },
                            color = OnSurfaceMid.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.displayList,
                        key = { it.localId }
                    ) { broadcast ->
                        BroadcastCard(
                            broadcast = broadcast,
                            onRead = { viewModel.markAsRead(broadcast.localId) },
                            onAcknowledge = { viewModel.acknowledge(broadcast.localId) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BroadcastTabBar(
    selected: BroadcastViewTab,
    unreadCount: Int,
    emergencyCount: Int,
    onTabSelected: (BroadcastViewTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BroadcastViewTab.values().forEach { tab ->
            val badge = when (tab) {
                BroadcastViewTab.UNREAD -> unreadCount
                BroadcastViewTab.EMERGENCY -> emergencyCount
                else -> 0
            }
            FilterChip(
                selected = selected == tab,
                onClick = { onTabSelected(tab) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(tab.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        if (badge > 0) {
                            Box(
                                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(50))
                                    .background(if (tab == BroadcastViewTab.EMERGENCY) DangerRed else Color(0xFFFF9800)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(badge.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GoldenYellow,
                    selectedLabelColor = NavyBlueDark,
                    containerColor = Surface1.copy(alpha = 0.5f),
                    labelColor = OnSurfaceMid
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == tab,
                    borderColor = Surface1,
                    selectedBorderColor = GoldenYellow
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Broadcast Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BroadcastCard(
    broadcast: BroadcastEntity,
    onRead: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val isEmergency = broadcast.type == "EMERGENCY" ||
            broadcast.priority == "CRITICAL" || broadcast.priority == "HIGH"
    val isUnread = broadcast.status != "ACKNOWLEDGED" && broadcast.status != "READ"
    val isAcknowledged = broadcast.status == "ACKNOWLEDGED"

    val borderColor = when {
        isEmergency -> DangerRed.copy(alpha = 0.6f)
        isUnread -> GoldenYellow.copy(alpha = 0.4f)
        else -> Surface1.copy(alpha = 0.5f)
    }

    // Expand/collapse for content
    var expanded by remember { mutableStateOf(isEmergency) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isEmergency -> DangerRed.copy(alpha = 0.08f)
                isUnread -> GoldenYellow.copy(alpha = 0.04f)
                else -> Surface1.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable {
                expanded = !expanded
                if (isUnread) onRead()
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Priority badge row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isEmergency) {
                            Surface(
                                color = DangerRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "⚠ EMERGENCY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DangerRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        // Unread dot
                        if (isUnread) {
                            Box(
                                modifier = Modifier.size(8.dp)
                                    .background(GoldenYellow, RoundedCornerShape(50))
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        broadcast.title,
                        fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isEmergency) DangerRed.copy(alpha = 0.95f) else OnSurface
                    )
                }

                Text(
                    formatTimestamp(broadcast.createdAt),
                    fontSize = 10.sp,
                    color = OnSurfaceMid.copy(alpha = 0.5f)
                )
            }

            // Sender
            Spacer(Modifier.height(4.dp))
            Text(
                "FROM: ${broadcast.senderName.uppercase()}",
                fontSize = 10.sp,
                color = OnSurfaceMid.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            // Content (expandable)
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        broadcast.content,
                        fontSize = 14.sp,
                        color = OnSurface.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }
            }

            if (!expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    broadcast.content,
                    fontSize = 12.sp,
                    color = OnSurfaceMid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ACK button for unacknowledged broadcasts
            if (!isAcknowledged) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEmergency) DangerRed else GoldenYellow
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = NavyBlueDark, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ACKNOWLEDGE", color = NavyBlueDark, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "✓ ACKNOWLEDGED",
                    fontSize = 10.sp,
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}

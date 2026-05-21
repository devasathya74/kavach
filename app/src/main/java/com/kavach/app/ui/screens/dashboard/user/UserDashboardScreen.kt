package com.kavach.app.ui.screens.dashboard.user

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.navigation.Screen
import com.kavach.app.ui.theme.*

/**
 * UserDashboardScreen — Mission Execution Console.
 *
 * DESIGN PHILOSOPHY:
 * - Large tap targets (field use: gloves, sunlight, stress)
 * - Low text density — only what matters
 * - No operational intelligence visible
 * - Emergency banner always on top
 * - Simple, immediate, fast
 *
 * NOT A PILOT DASHBOARD LITE.
 * This shows: My Orders, My Broadcasts, Report, Device Status, SOS.
 * Nothing else.
 */
@Composable
fun UserDashboardScreen(
    viewModel: UserDashboardViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showSosDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = { UserTopBar(state = state, onNavigate = onNavigate) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Emergency Banner (shown when emergency broadcast exists) ──
            AnimatedVisibility(
                visible = state.hasEmergencyBroadcast,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                EmergencyBanner(
                    title = state.emergencyTitle,
                    onClick = { onNavigate(Screen.MyBroadcasts.route) }
                )
            }

            // ── Section: My Missions ─────────────────────────────────────
            UserSectionLabel("MY MISSIONS")

            // My Orders — large card
            MissionCard(
                icon = Icons.Default.Assignment,
                title = "MY ORDERS",
                subtitle = when {
                    state.metrics.pendingOrders > 0 -> "${state.metrics.pendingOrders} PENDING ACKNOWLEDGMENT"
                    else -> "ALL ORDERS UP TO DATE"
                },
                badge = if (state.metrics.pendingOrders > 0) state.metrics.pendingOrders else null,
                badgeColor = if (state.metrics.pendingOrders > 0) Color(0xFFFF9800) else GoldenYellow,
                accentColor = Color(0xFF60A5FA),
                onClick = { onNavigate(Screen.MyOrders.route) }
            )

            // My Broadcasts
            MissionCard(
                icon = Icons.Default.Campaign,
                title = "BROADCASTS",
                subtitle = when {
                    state.metrics.emergencyBroadcasts > 0 -> "⚠ ${state.metrics.emergencyBroadcasts} EMERGENCY UNREAD"
                    state.metrics.unreadBroadcasts > 0 -> "${state.metrics.unreadBroadcasts} UNREAD"
                    else -> "NO NEW BROADCASTS"
                },
                badge = if (state.metrics.unreadBroadcasts > 0) state.metrics.unreadBroadcasts else null,
                badgeColor = if (state.metrics.emergencyBroadcasts > 0) DangerRed else GoldenYellow,
                accentColor = GoldenYellow,
                onClick = { onNavigate(Screen.MyBroadcasts.route) }
            )

            // ── Section: Field Services ──────────────────────────────────
            UserSectionLabel("FIELD SERVICES")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Report Incident — large, prominent
                FieldActionCard(
                    icon = Icons.Default.ReportProblem,
                    label = "REPORT\nINCIDENT",
                    color = Color(0xFFF87171),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.UserReportIncident.route) }
                )

                // Device Status
                FieldActionCard(
                    icon = if (state.isOnline) Icons.Default.SignalWifi4Bar else Icons.Default.SignalWifiOff,
                    label = when {
                        !state.isOnline -> "OFFLINE"
                        state.wsConnected -> "CONNECTED"
                        else -> "SYNCING..."
                    },
                    color = when {
                        !state.isOnline -> Color(0xFF94A3B8)
                        state.wsConnected -> Color(0xFF4ADE80)
                        else -> Color(0xFFFBBF24)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.MySyncStatus.route) }
                )
            }

            // ── Quick Actions ────────────────────────────────────────────
            UserSectionLabel("QUICK ACTIONS")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionChip(
                    icon = Icons.Default.WarningAmber,
                    label = "SOS",
                    color = DangerRed,
                    modifier = Modifier.weight(1f),
                    onClick = { showSosDialog = true }
                )
                QuickActionChip(
                    icon = Icons.Default.AddAlert,
                    label = "REPORT",
                    color = Color(0xFFF87171),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.UserReportIncident.route) }
                )
                QuickActionChip(
                    icon = Icons.Default.ListAlt,
                    label = "ORDERS",
                    color = Color(0xFF60A5FA),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.MyOrders.route) }
                )
                QuickActionChip(
                    icon = Icons.Default.Campaign,
                    label = "INBOX",
                    color = GoldenYellow,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(Screen.MyBroadcasts.route) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Footer
            Text(
                "KAVACH FIELD OPS · ${state.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceMid.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            containerColor = NavyBlueDarker,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "ACTIVATE EMERGENCY SOS?",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    "This will immediately transmit an emergency alert to all command centers. Use ONLY in life-threatening situations.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerSos()
                        showSosDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("ACTIVATE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserTopBar(state: UserDashboardState, onNavigate: (String) -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    state.name.uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = OnSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Connectivity dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (state.isOnline) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Text(
                        if (state.isOnline) "ONLINE" else "OFFLINE",
                        fontSize = 10.sp,
                        color = if (state.isOnline) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text("·", fontSize = 10.sp, color = OnSurfaceMid)
                    Text(
                        "PNO ${state.pno}",
                        fontSize = 10.sp,
                        color = OnSurfaceMid,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { onNavigate(Screen.Profile.route) }) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    tint = GoldenYellow,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
    )
}

@Composable
private fun EmergencyBanner(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(DangerRed.copy(alpha = 0.9f), Color(0xFFFF6B35).copy(alpha = 0.9f))
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.NotificationImportant,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "⚠ EMERGENCY BROADCAST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UserSectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = OnSurfaceMid.copy(alpha = 0.7f),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/**
 * MissionCard — Large, high-contrast, field-readable card.
 * Height = 100.dp so it's easy to tap with gloves.
 */
@Composable
private fun MissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: Int?,
    badgeColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1.copy(alpha = 0.6f))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = OnSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = if (subtitle.contains("EMERGENCY") || subtitle.contains("PENDING"))
                        Color(0xFFFF9800) else OnSurfaceMid
                )
            }

            // Badge
            if (badge != null && badge > 0) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        color = NavyBlueDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = OnSurfaceMid.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * FieldActionCard — Square card for field services.
 * Large enough to tap under stress or with gloves.
 */
@Composable
private fun FieldActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * QuickActionChip — Compact action for the Quick Actions row.
 */
@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                letterSpacing = 0.5.sp
            )
        }
    }
}

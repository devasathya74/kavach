package com.kavach.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.navigation.Screen
import com.kavach.app.ui.theme.*
import com.kavach.app.util.ConnectionStatus

/**
 * CommandCenterScreen — For SENANAYAK / COMMANDING_OFFICER role (highest authority).
 *
 * Modules:
 *   ✓ Personnel       → PersonnelListScreen   (backend integrated)
 *   ✓ Approvals       → ApprovalListScreen   (backend integrated)
 *   ✓ Audit Logs      → AuditTimelineScreen  (backend integrated)
 *   ✓ Broadcast       → BroadcastCenterScreen (backend integrated)
 *   ✓ Incidents       → IncidentCenterScreen (backend integrated)
 *   ✓ Connectivity    → ConnectivityScreen   (backend integrated)
 *   ○ Global Ops Map  → ComingSoon
 *   ○ Threat Matrix   → ComingSoon
 *   ○ Emergency Override → ComingSoon
 *   ○ Deployment Feed → ComingSoon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(
    onNavigate : (String) -> Unit,
    viewModel  : DashboardViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionStore = viewModel.sessionDataStore
    val name        by sessionStore.name.collectAsState(initial = "Senanayak")
    val rank        by sessionStore.rank.collectAsState(initial = "")
    val unit        by sessionStore.unit.collectAsState(initial = "")
    val connStatus  by viewModel.connectionStatus.collectAsState(initial = ConnectionStatus.AVAILABLE)
    val isOnline     = connStatus == ConnectionStatus.AVAILABLE

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Red authority indicator for Senanayak
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFFDC2626), Color(0xFF7F1D1D))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1).uppercase(), color = Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("KAVACH", color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Command Center", color = DangerRed.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                    }
                },
                actions = {
                    ConnStatusChip(isOnline)
                    IconButton(onClick = { onNavigate(Screen.Profile.route) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile",
                            tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement   = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = if (rank.isNotBlank()) "$rank  $name" else name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                    if (unit.isNotBlank()) Text(unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.55f))
                    Text("COMMAND AUTHORITY", color = DangerRed.copy(alpha = 0.8f),
                        fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Status strip
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(
                                DangerRed.copy(alpha = 0.08f), Color.Transparent
                            )),
                            RoundedCornerShape(12.dp)
                        )
                        .background(Surface1, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CmdStatusChip("PENDING", uiState.metrics.incidentCount.toString(), DangerRed)
                    CmdStatusChip("INCIDENTS", uiState.metrics.incidentCount.toString(), Color(0xFFFB923C))
                    CmdStatusChip("BROADCASTS", uiState.metrics.broadcastCount.toString(), GoldenYellow)
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text("Strategic Operations", style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f))
            }

            // ── ACTIVE MODULES ────────────────────────────────────

            item {
                DashTile("Personnel", Icons.Filled.Group, Color(0xFFA855F7), active = true) {
                    onNavigate(Screen.UserManagement.route)
                }
            }
            item {
                DashTile("Approvals", Icons.Filled.CheckCircle, Color(0xFF4ADE80), active = true) {
                    onNavigate(Screen.PendingApprovals.route)
                }
            }
            item {
                DashTile("Incidents", Icons.Filled.Warning, DangerRed, active = true) {
                    onNavigate(Screen.IncidentCenter.route)
                }
            }
            item {
                DashTile("Audit Logs", Icons.Filled.History, Color(0xFF94A3B8), active = true) {
                    onNavigate(Screen.AuditCenter.route)
                }
            }
            item {
                DashTile("Broadcast", Icons.Filled.Campaign, GoldenYellow, active = true) {
                    onNavigate(Screen.BroadcastCenter.route)
                }
            }
            item {
                DashTile("Connectivity", Icons.Filled.Wifi, Color(0xFF38BDF8), active = true) {
                    onNavigate(Screen.ConnectivityTest.route)
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text("Strategic Command (Integration Pending)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.4f))
            }

            // ── INTEGRATION PENDING ───────────────────────────────
            item {
                DashTile("Ops Map", Icons.Filled.Map, Color(0xFF34D399), active = false) {
                    onNavigate(Screen.ComingSoon.createRoute("Global Operations Map"))
                }
            }
            item {
                DashTile("Threat Matrix", Icons.Filled.Shield, DangerRed, active = false) {
                    onNavigate(Screen.ComingSoon.createRoute("Threat Matrix"))
                }
            }
            item {
                DashTile("Emergency Override", Icons.Filled.WarningAmber, Color(0xFFFB923C), active = false) {
                    onNavigate(Screen.ComingSoon.createRoute("Emergency Override"))
                }
            }
            item {
                DashTile("Deployment Feed", Icons.Filled.LocationOn, Color(0xFFA78BFA), active = false) {
                    onNavigate(Screen.ComingSoon.createRoute("Deployment Feed"))
                }
            }
        }
    }
}

@Composable
private fun CmdStatusChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 0.5.sp)
    }
}

@Composable
private fun ConnStatusChip(isOnline: Boolean) {
    Surface(
        color = (if (isOnline) SuccessGreen else DangerRed).copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) SuccessGreen else DangerRed)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isOnline) "ONLINE" else "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOnline) SuccessGreen else DangerRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DashTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    active: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(enabled = active, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (active) color.copy(alpha = 0.05f) else Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .alpha(if (active) 1f else 0.5f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (active) color else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                    if (!active) {
                        Text(
                            text = "जल्द आ रहा है",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldenYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (active) Color.White else Color.Gray
                )
            }
        }
    }
}


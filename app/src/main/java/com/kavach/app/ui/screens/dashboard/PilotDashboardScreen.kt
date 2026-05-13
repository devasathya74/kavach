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
import androidx.compose.ui.draw.clip
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
 * PilotDashboardScreen — For PILOT / PILOT_USER role (supervisory officers).
 *
 * Modules:
 *   ✓ Personnel Management → PersonnelListScreen   (backend integrated)
 *   ✓ Pending Approvals   → ApprovalListScreen    (backend integrated)
 *   ✓ Incident Center     → IncidentCenterScreen  (backend integrated)
 *   ✓ Broadcast Center    → BroadcastCenterScreen (backend integrated)
 *   ✓ OTA Update          → OtaUpdateScreen       (backend integrated)
 *   ✓ Field Data          → FieldDataScreen        (backend integrated)
 *   ✓ Audit Logs          → AuditTimelineScreen   (backend integrated)
 *   ✓ Device Monitor      → DeviceStatusScreen    (backend integrated)
 *   ○ Analytics           → ComingSoon
 *   ○ Threat Monitoring   → ComingSoon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotDashboardScreen(
    onNavigate : (String) -> Unit,
    viewModel  : DashboardViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionStore = viewModel.sessionDataStore
    val name        by sessionStore.name.collectAsState(initial = "Pilot")
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
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape)
                                .background(Color(0xFF7C3AED)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1).uppercase(), color = Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("KAVACH", color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Pilot Command", color = Color(0xFFA78BFA).copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    ConnStatusChip(isOnline)
                    IconButton(onClick = { onNavigate(Screen.Profile.route) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile",
                            tint = Color(0xFFA78BFA))
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
                    Text("PILOT OFFICER", color = Color(0xFFA78BFA).copy(alpha = 0.8f),
                        fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Status strip
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Surface1, RoundedCornerShape(12.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PilotStatusChip("APPROVALS", uiState.incidentCount.toString(), Color(0xFFA78BFA))
                    PilotStatusChip("INCIDENTS", uiState.incidentCount.toString(), DangerRed)
                    PilotStatusChip("BROADCASTS", uiState.broadcastCount.toString(), GoldenYellow)
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text("Operations Control", style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f))
            }

            // ── ACTIVE MODULES ────────────────────────────────────

            item {
                DashTile("Personnel", Icons.Filled.Group, Color(0xFFA855F7), active = true) {
                    onNavigate(Screen.UserManagement.route)
                }
            }
            item {
                DashTile("Approvals", Icons.Filled.CheckCircle, Color(0xFF4ADE80), active = true,
                    badge = uiState.incidentCount.toString()
                ) { onNavigate(Screen.PendingApprovals.route) }
            }
            item {
                DashTile("Incidents", Icons.Filled.Warning, DangerRed, active = true) {
                    onNavigate(Screen.IncidentCenter.route)
                }
            }
            item {
                DashTile("Broadcast", Icons.Filled.Campaign, GoldenYellow, active = true) {
                    onNavigate(Screen.BroadcastCenter.route)
                }
            }
            item {
                DashTile("OTA Update", Icons.Filled.SystemUpdate, Color(0xFF38BDF8), active = true) {
                    onNavigate(Screen.OtaUpdate.route)
                }
            }
            item {
                DashTile("Field Data", Icons.Filled.FolderOpen, Color(0xFFFB923C), active = true) {
                    onNavigate(Screen.FieldData.route)
                }
            }
            item {
                DashTile("Audit Logs", Icons.Filled.History, Color(0xFF94A3B8), active = true) {
                    onNavigate(Screen.AuditCenter.route)
                }
            }
            item {
                DashTile("Devices", Icons.Filled.SettingsInputAntenna, Color(0xFF34D399), active = true) {
                    onNavigate(Screen.DeviceMonitor.route)
                }
            }

            // ── INTEGRATION PENDING ───────────────────────────────
            item {
                DashTile("Analytics", Icons.Filled.Analytics, Color(0xFFA78BFA), active = false) {
                    onNavigate(Screen.ComingSoon.createRoute("Analytics"))
                }
            }
            item {
                DashTile("Threat Monitor", Icons.Filled.Shield, DangerRed, active = false) {
                    onNavigate(Screen.ComingSoon.createRoute("Threat Monitoring"))
                }
            }
        }
    }
}

@Composable
private fun PilotStatusChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 0.5.sp)
    }
}

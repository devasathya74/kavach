package com.kavach.app.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedDashboardScreen(
    role: String,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Permission Matrix
    val isSupervisor = role == "PILOT" || role == "COMMANDING_OFFICER"
    val isAdmin = role == "COMMANDING_OFFICER"

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KAVACH Dashboard", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(role.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = GoldenYellow)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = { onNavigate("profile") }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = GoldenYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Text("Operational Status", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
            }

            item {
                StatusTile(
                    title = "Incidents",
                    count = uiState.incidentCount.toString(),
                    icon = Icons.Filled.ReportProblem,
                    color = DangerRed,
                    onClick = { onNavigate("live_broadcast") }
                )
            }
            item {
                StatusTile(
                    title = "Broadcasts",
                    count = uiState.broadcastCount.toString(),
                    icon = Icons.Filled.Campaign,
                    color = GoldenYellow,
                    onClick = { onNavigate("broadcast_inbox") }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Text("Command & Control", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
            }

            item {
                ActionTile(
                    title = "Live Feed",
                    icon = Icons.Filled.LiveTv,
                    color = SuccessGreen,
                    onClick = { onNavigate("live_broadcast") }
                )
            }
            
            item {
                ActionTile(
                    title = "Orders",
                    icon = Icons.Filled.Assignment,
                    color = Color(0xFF38BDF8),
                    onClick = { onNavigate("order_list") }
                )
            }

            // --- SUPERVISOR ONLY TILES ---
            if (isSupervisor) {
                item(span = { GridItemSpan(2) }) {
                    Text("Administration", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }

                item {
                    ActionTile(
                        title = "Personnel",
                        icon = Icons.Filled.Group,
                        color = Color(0xFFA855F7),
                        onClick = { onNavigate("user_management") }
                    )
                }

                item {
                    ActionTile(
                        title = "Devices",
                        icon = Icons.Filled.Devices,
                        color = Color(0xFFF43F5E),
                        onClick = { onNavigate("device_monitor") }
                    )
                }

                item {
                    ActionTile(
                        title = "Broadcast Center",
                        icon = Icons.Filled.Podcasts,
                        color = Color(0xFF10B981),
                        onClick = { onNavigate("broadcast_center") }
                    )
                }

                item {
                    ActionTile(
                        title = "Audit Logs",
                        icon = Icons.Filled.HistoryEdu,
                        color = Color(0xFFF59E0B),
                        onClick = { onNavigate("audit_center") }
                    )
                }
            }

            // --- SYSTEM MAINTENANCE ---
            if (isSupervisor) {
                item(span = { GridItemSpan(2) }) {
                    Text("System", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }

                item {
                    ActionTile(
                        title = "OTA Updates",
                        icon = Icons.Filled.SystemUpdate,
                        color = Color(0xFF6366F1),
                        onClick = { onNavigate("ota_update") }
                    )
                }

                item {
                    ActionTile(
                        title = "Field Data",
                        icon = Icons.Filled.Storage,
                        color = Color(0xFF94A3B8),
                        onClick = { onNavigate("field_data") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTile(title: String, count: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(count, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ActionTile(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

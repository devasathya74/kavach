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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.navigation.Screen
import com.kavach.app.ui.theme.*
import com.kavach.app.util.ConnectionStatus

/**
 * FieldDashboard — For NORMAL_USER / USER role (field officers).
 *
 * Modules:
 *   ✓ Training      → TrainingListScreen   (backend integrated)
 *   ✓ Orders        → OrderListScreen      (backend integrated)
 *   ✓ Profile       → ProfileScreen        (backend integrated)
 *   ○ Notifications → ComingSoon
 *   ○ Conference    → ComingSoon
 *   ○ Broadcasts    → BroadcastInboxScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDashboardScreen(
    onNavigate : (String) -> Unit,
    viewModel  : DashboardViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionStore = viewModel.sessionDataStore
    val name        by sessionStore.name.collectAsState(initial = "Officer")
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
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(GoldenYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(1).uppercase(),
                                color = NavyBlueDark,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("KAVACH", color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Field Operations", color = GoldenYellow.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    ConnStatusChip(isOnline)
                    IconButton(onClick = { onNavigate(Screen.Profile.route) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = GoldenYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement   = Arrangement.spacedBy(16.dp)
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
                    Text("FIELD OFFICER", color = GoldenYellow.copy(alpha = 0.7f),
                        fontSize = 11.sp, letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold)
                }
            }

            // Status strip
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Surface1, RoundedCornerShape(12.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusChip("TRAINING", uiState.trainingCount.toString(), GoldenYellow)
                    StatusChip("ORDERS", uiState.orderCount.toString(), Color(0xFF38BDF8))
                    StatusChip("BROADCASTS", uiState.broadcastCount.toString(), SuccessGreen)
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text("मुख्य कार्य", style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }

            // ── ACTIVE MODULES ─────────────────────────────────────

            item {
                DashTile("Training", Icons.Filled.School, Color(0xFF4ADE80),
                    active = true, badge = uiState.trainingCount.toString()
                ) { onNavigate(Screen.TrainingList.route) }
            }

            item {
                DashTile("Orders", Icons.Filled.Assignment, Color(0xFF38BDF8),
                    active = true, badge = uiState.orderCount.toString()
                ) { onNavigate(Screen.OrderList.route) }
            }

            item {
                DashTile("Broadcasts", Icons.Filled.Campaign, GoldenYellow,
                    active = true
                ) { onNavigate(Screen.BroadcastInbox.route) }
            }

            item {
                DashTile("My Profile", Icons.Filled.Person, Color(0xFFA855F7),
                    active = true
                ) { onNavigate(Screen.Profile.route) }
            }

            // ── INTEGRATION PENDING ────────────────────────────────

            item {
                DashTile("Notifications", Icons.Filled.Notifications, Color(0xFFEA580C),
                    active = false
                ) { onNavigate(Screen.ComingSoon.createRoute("Notifications")) }
            }

            item {
                DashTile("Conference", Icons.Filled.VideoCall, Color(0xFF94A3B8),
                    active = false
                ) { onNavigate(Screen.ComingSoon.createRoute("Video Conference")) }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f),
            letterSpacing = 0.5.sp)
    }
}

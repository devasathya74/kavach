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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.kavach.app.ui.navigation.Screen
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.ui.theme.*
import com.kavach.app.util.ConnectionStatus

/**
 * Stabilized Unified Dashboard for Pilot Phase.
 * Authoritative: Local Session & Cache.
 * Connectivity: Passive indicators only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedDashboardScreen(
    role: String,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionStore = viewModel.sessionDataStore
    
    // ── 1. Local Profile Data (Authoritative) ────────────────────
    val name by sessionStore.name.collectAsState(initial = "Officer")
    val rank by sessionStore.rank.collectAsState(initial = "Loading...")
    val unit by sessionStore.unit.collectAsState(initial = "Unit Not Assigned")
    val connectionStatus by viewModel.connectionStatus.collectAsState(initial = ConnectionStatus.AVAILABLE)

    val context = LocalContext.current

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GoldenYellow),
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
                            Text(
                                text = "KAVACH",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pilot Phase v2.1",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldenYellow.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    // Connection Chip (Passive)
                    val isOnline = connectionStatus == ConnectionStatus.AVAILABLE
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
                    IconButton(onClick = { onNavigate(Screen.Profile.route) }) {
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
            // ── 2. Header / Welcome ──────────────────────────────────
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "नमस्ते, $rank $name",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // ── 3. Status Strip (Cached/Offline-Safe) ───────────────
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface1, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusIndicator("कुल कर्मी", uiState.incidentCount.toString(), GoldenYellow) // Using incidentCount as dummy for now
                    StatusIndicator("सक्रिय", "12", SuccessGreen)
                    StatusIndicator("लंबित", uiState.broadcastCount.toString(), DangerRed)
                }
            }

            // ── 4. Main Actions ─────────────────────────────────────
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "मुख्य कार्य",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 1. PERSONNEL MANAGEMENT (ACTIVE)
            item {
                ActionCard(
                    title = "Personnel\nManagement",
                    icon = Icons.Filled.Group,
                    color = Color(0xFFA855F7),
                    onClick = { onNavigate(Screen.UserManagement.route) }
                )
            }
            
            // 2. DUTY MANAGEMENT (COMING SOON)
            item {
                ActionCard(
                    title = "Duty\nManagement",
                    icon = Icons.Filled.Assignment,
                    color = Color(0xFF38BDF8),
                    enabled = false,
                    onClick = { Toast.makeText(context, "Module under stabilization", Toast.LENGTH_SHORT).show() }
                )
            }

            // 3. BROADCAST CENTER (COMING SOON)
            item {
                ActionCard(
                    title = "Broadcast\nCenter",
                    icon = Icons.Filled.Campaign,
                    color = GoldenYellow,
                    enabled = false,
                    onClick = { Toast.makeText(context, "Module under stabilization", Toast.LENGTH_SHORT).show() }
                )
            }

            // 4. TRAINING MODULES (COMING SOON)
            item {
                ActionCard(
                    title = "Training\nModules",
                    icon = Icons.Filled.School,
                    color = SuccessGreen,
                    enabled = false,
                    onClick = { Toast.makeText(context, "Module under stabilization", Toast.LENGTH_SHORT).show() }
                )
            }

            // 5. INCIDENT REPORTS (COMING SOON)
            item {
                ActionCard(
                    title = "Incident\nReports",
                    icon = Icons.Filled.Analytics,
                    color = DangerRed,
                    enabled = false,
                    onClick = { Toast.makeText(context, "Module under stabilization", Toast.LENGTH_SHORT).show() }
                )
            }

            // 6. DEVICE MONITOR (COMING SOON)
            item {
                ActionCard(
                    title = "Device\nMonitor",
                    icon = Icons.Filled.SettingsInputAntenna,
                    color = Color(0xFF94A3B8),
                    enabled = false,
                    onClick = { Toast.makeText(context, "Module under stabilization", Toast.LENGTH_SHORT).show() }
                )
            }

            // ── 5. System Footer ───────────────────────────────────
            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "KAVACH सुरक्षा प्रणाली • 2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatusIndicator(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun ActionCard(
    title: String, 
    icon: ImageVector, 
    color: Color, 
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(enabled = true, onClick = onClick), // Always clickable for Toast even if disabled
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (enabled) color.copy(alpha = 0.05f) else Color.Transparent, 
                                Color.Transparent
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier.padding(16.dp).alpha(if (enabled) 1f else 0.5f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) color else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                    if (!enabled) {
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
                    color = if (enabled) Color.White else Color.Gray
                )
            }
        }
    }
}

package com.kavach.app.ui.screens.pilot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*

data class PilotModule(val title: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotDashboardScreen(onLogout: () -> Unit = {}, onNavigate: (String) -> Unit = {}) {
    val modules =
            listOf(
                    PilotModule("USERS", Icons.Filled.People, "route_users"),
                    PilotModule(
                            "PENDING APPROVALS",
                            Icons.Filled.PendingActions,
                            "route_approvals"
                    ),
                    PilotModule("OTA UPDATE", Icons.Filled.SystemUpdate, "route_ota"),
                    PilotModule("INCIDENTS", Icons.Filled.ReportProblem, "route_incidents"),
                    PilotModule("DEVICES", Icons.Filled.Devices, "route_devices"),
                    PilotModule("BROADCAST", Icons.Filled.Campaign, "route_broadcast"),
                    PilotModule("FIELD DATA", Icons.Filled.Storage, "route_field_data"),
                    PilotModule("AUDIT", Icons.Filled.History, "route_audit")
            )

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    "Pilot Dashboard",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = onLogout) {
                                Icon(
                                        Icons.Filled.Logout,
                                        contentDescription = "Logout",
                                        tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
                )
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color(0xFF0F172A)) // Dark tactical background
                                .padding(padding)
                                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // System Status Banner
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                                modifier =
                                        Modifier.size(12.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SYSTEM ONLINE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Phase: PILOT", color = GoldenYellow, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Modules Grid
            LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
            ) { items(modules) { module -> PilotModuleCard(module, onNavigate) } }
        }
    }
}

@Composable
fun PilotModuleCard(module: PilotModule, onNavigate: (String) -> Unit) {
    Card(
            modifier =
                    Modifier.fillMaxWidth().aspectRatio(1f).clickable { onNavigate(module.route) },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                    imageVector = module.icon,
                    contentDescription = module.title,
                    tint = GoldenYellow,
                    modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = module.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

package com.kavach.app.ui.screens.dashboard.pilot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kavach.app.ui.navigation.Screen
import com.kavach.app.ui.screens.dashboard.components.DashboardCard
import com.kavach.app.ui.screens.dashboard.components.DashboardTopBar
import com.kavach.app.ui.theme.NavyBlueDark

import com.kavach.app.ui.screens.dashboard.DashboardMetrics
import com.kavach.app.ui.screens.dashboard.components.DashboardCard
import com.kavach.app.ui.screens.dashboard.components.DashboardTopBar
import com.kavach.app.ui.theme.NavyBlueDark

@Composable
fun PilotDashboardScreen(
    name: String,
    metrics: DashboardMetrics = DashboardMetrics(),
    isOnline: Boolean,
    wsConnected: Boolean,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            DashboardTopBar(
                name = name,
                roleTitle = "Pilot Phase Supervisor",
                isOnline = isOnline,
                wsConnected = wsConnected,
                onProfileClick = { onNavigate(Screen.Profile.route) }
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
                Text(
                    text = "Operational Oversight",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                DashboardCard(
                    title = "Personnel\nHub",
                    icon = Icons.Filled.Group,
                    color = Color(0xFF60A5FA),
                    metric = if (metrics.personnelCount > 0) metrics.personnelCount.toString() else null,
                    onClick = { onNavigate(Screen.UserManagement.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Incident\nCenter",
                    icon = Icons.Filled.Emergency,
                    color = Color(0xFFF87171),
                    metric = if (metrics.incidentCount > 0) metrics.incidentCount.toString() else null,
                    onClick = { onNavigate(Screen.IncidentCenter.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Broadcast\nTerminal",
                    icon = Icons.Filled.Campaign,
                    color = Color(0xFFF59E0B),
                    metric = if (metrics.broadcastCount > 0) metrics.broadcastCount.toString() else null,
                    onClick = { onNavigate(Screen.BroadcastCenter.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Command\nOrders",
                    icon = Icons.Filled.ListAlt,
                    color = Color(0xFF818CF8),
                    onClick = { onNavigate(Screen.OrderList.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Pending\nApprovals",
                    icon = Icons.Filled.HowToReg,
                    color = Color(0xFF4ADE80),
                    metric = if (metrics.approvalCount > 0) metrics.approvalCount.toString() else null,
                    onClick = { onNavigate(Screen.PendingApprovals.route) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                DashboardCard(
                    title = "Register\nPersonnel",
                    icon = Icons.Filled.PersonAdd,
                    color = Color(0xFFA855F7),
                    onClick = { onNavigate(Screen.UserRegistration.route) }
                )
            }

            item {
                DashboardCard(
                    title = "OTA\nUpdate",
                    icon = Icons.Filled.SystemUpdate,
                    color = Color(0xFF94A3B8),
                    onClick = { onNavigate(Screen.OtaUpdate.route) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "KAVACH PILOT OPS • 2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

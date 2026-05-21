package com.kavach.app.ui.screens.dashboard.user

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
import com.kavach.app.ui.screens.dashboard.DashboardMetrics
import com.kavach.app.ui.screens.dashboard.components.DashboardCard
import com.kavach.app.ui.screens.dashboard.components.DashboardTopBar
import com.kavach.app.ui.theme.NavyBlueDark

@Composable
fun UserDashboardScreen(
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
                roleTitle = "Field Console",
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
                    text = "Operational Orders",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                DashboardCard(
                    title = "My Orders",
                    icon = Icons.Filled.Assignment,
                    color = Color(0xFF60A5FA),
                    metric = if (metrics.orderCount > 0) metrics.orderCount.toString() else null,
                    onClick = { onNavigate(Screen.OrderList.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Broadcasts",
                    icon = Icons.Filled.Campaign,
                    color = Color(0xFFF59E0B),
                    metric = if (metrics.broadcastCount > 0) metrics.broadcastCount.toString() else null,
                    onClick = { onNavigate(Screen.BroadcastInbox.route) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Field Services",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }

            item {
                DashboardCard(
                    title = "Report\nIncident",
                    icon = Icons.Filled.ReportProblem,
                    color = Color(0xFFF87171),
                    onClick = { onNavigate(Screen.IncidentCenter.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Unit\nStatus",
                    icon = Icons.Filled.Security,
                    color = Color(0xFF4ADE80),
                    onClick = { onNavigate(Screen.ComingSoon.createRoute("Unit Status")) }
                )
            }

            item {
                DashboardCard(
                    title = "Training",
                    icon = Icons.Filled.School,
                    color = Color(0xFFA855F7),
                    metric = if (metrics.trainingCount > 0) metrics.trainingCount.toString() else null,
                    onClick = { onNavigate(Screen.TrainingList.route) }
                )
            }

            item {
                DashboardCard(
                    title = "Attendance",
                    icon = Icons.Filled.Fingerprint,
                    color = Color(0xFF94A3B8),
                    onClick = { onNavigate(Screen.ComingSoon.createRoute("Attendance")) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "KAVACH FIELD OPS • 2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

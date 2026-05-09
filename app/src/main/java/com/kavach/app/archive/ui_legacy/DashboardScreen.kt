package com.kavach.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.Order
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onGoToLive     : () -> Unit,
    onGoToBroadcasts: () -> Unit,
    onGoToOrders   : () -> Unit,
    onGoToProfile  : () -> Unit,
    viewModel      : DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = {
                    Text("KAVACH Dashboard", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = onGoToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = GoldenYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 1. Mission Status Overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusWidget(
                    modifier = Modifier.weight(1f),
                    title = "घटनाएँ (Incidents)",
                    count = if (uiState.isMissionLoading) "..." else uiState.incidentCount.toString(),
                    icon = Icons.Filled.ReportProblem,
                    color = DangerRed,
                    onClick = onGoToLive
                )
                StatusWidget(
                    modifier = Modifier.weight(1f),
                    title = "ब्रॉडकास्ट (Alerts)",
                    count = if (uiState.isMissionLoading) "..." else uiState.broadcastCount.toString(),
                    icon = Icons.Filled.Campaign,
                    color = GoldenYellow,
                    onClick = onGoToBroadcasts
                )
            }

            Text("मुख्य कार्य (Main Tasks)", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)

            if (uiState.isOrdersLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldenYellow)
                }
            } else if (uiState.orders.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("कोई लंबित कार्य नहीं है", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else {
                uiState.orders.forEach { order ->
                    OrderCard(
                        title = order.title,
                        priority = order.priority,
                        status = if (order.isAcknowledged) "ACKNOWLEDGED" else "PENDING",
                        timeRemaining = "Deadline: ${order.deadline}",
                        onClick = onGoToOrders
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Operational Tools", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)

            QuickActionTile(
                title = "Live Mission Feed",
                icon = Icons.Filled.LiveTv,
                color = SuccessGreen,
                onClick = onGoToLive
            )
            
            QuickActionTile(
                title = "Device Health Monitor",
                icon = Icons.Filled.Security,
                color = Color(0xFF38BDF8),
                onClick = { /* TODO */ }
            )
        }
    }
}

@Composable
private fun StatusWidget(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(count, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun OrderCard(title: String, priority: String, status: String, timeRemaining: String, onClick: () -> Unit) {
    val borderColor = when (priority) {
        "HIGH" -> DangerRed
        "MEDIUM" -> GoldenYellow
        else -> Color.Gray
    }
    
    val statusColor = when (status) {
        "OVERDUE" -> DangerRed
        "PENDING" -> GoldenYellow
        "ACKNOWLEDGED" -> SuccessGreen
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(timeRemaining, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestrictedDashboardScreen(
    onRetry  : () -> Unit,
    onLogout : () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = { Text("LIMITED MODE", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Session Verification Incomplete",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Network issues prevented backend verification. You are in Limited Mode. Data is read-only and unverified.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = NavyBlueDark)
                Spacer(Modifier.width(8.dp))
                Text("Retry Verification", color = NavyBlueDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DangerRed)
            ) {
                Text("Logout", color = DangerRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

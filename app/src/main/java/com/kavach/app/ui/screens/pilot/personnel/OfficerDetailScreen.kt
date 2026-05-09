package com.kavach.app.ui.screens.pilot.personnel

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.kavach.app.ui.components.RoleBadge
import com.kavach.app.ui.components.StatusBadge
import com.kavach.app.data.remote.dto.personnel.DeviceDto
import com.kavach.app.data.remote.dto.personnel.OfficerDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerDetailScreen(
    viewModel: OfficerDetailViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OFFICER COMMAND PANEL", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (state.isLoading && state.officer == null) {
                SkeletonDetail()
            } else if (state.error != null) {
                ErrorState(message = state.error!!, onRetry = { viewModel.loadDetail() })
            } else if (state.officer != null) {
                OfficerDetailContent(
                    officer = state.officer!!,
                    onRevoke = { viewModel.revokeDevice(it) }
                )
            }
        }
    }
}

@Composable
fun SkeletonDetail() {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(Color.LightGray.copy(alpha = 0.3f)).align(Alignment.CenterHorizontally))
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(Color.LightGray.copy(alpha = 0.2f)))
        }
    }
}

@Composable
fun OfficerDetailContent(
    officer: OfficerDto,
    onRevoke: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { HeaderSection(officer) }

        item {
            DeviceIntelligencePanel(
                devices = officer.devices,
                onRevoke = onRevoke
            )
        }

        item {
            InfoSection("Personnel Context", Icons.Default.AssignmentInd) {
                InfoRow("PNO ID", officer.pno)
                InfoRow("Unit", officer.unit?.name ?: "N/A")
                InfoRow("Company", officer.profile?.company?.name ?: "N/A")
                InfoRow("Status", officer.profile?.serviceStatus ?: if (officer.isActive) "Active" else "Inactive")
            }
        }

        item {
            InfoSection("Personnel Activity Timeline", Icons.Default.History) {
                // Placeholder for actual timeline items from repository
                repeat(3) { index ->
                    ActivityItem(
                        action = if(index == 0) "DEVICE_REVOKED" else "LOGIN_SUCCESS",
                        severity = if(index == 0) "WARNING" else "INFO",
                        timestamp = "2026-05-07 10:15:2$index",
                        result = "SUCCESS"
                    )
                    if(index < 2) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }

        item {
            InfoSection("Operational Security", Icons.Default.Shield) {
                InfoRow("Role Authority", officer.role)
                InfoRow("Integrity State", officer.devices.firstOrNull()?.status ?: "N/A")
                InfoRow("Account Integrity", if (officer.isActive) "SECURE" else "LOCKED")
            }
        }
    }
}

@Composable
fun ActivityItem(action: String, severity: String, timestamp: String, result: String) {
    val severityColor = when(severity) {
        "CRITICAL", "SECURITY" -> Color(0xFFF44336) // Red
        "WARNING" -> Color(0xFFFF9800) // Orange
        else -> Color(0xFF4CAF50) // Green
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(8.dp).offset(y = 6.dp).clip(CircleShape).background(severityColor))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(action.replace("_", " "), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Surface(color = severityColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
            Text(result, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = severityColor)
        }
    }
}

@Composable
fun DeviceIntelligencePanel(
    devices: List<DeviceDto>,
    onRevoke: (String) -> Unit
) {
    InfoSection("Device Intelligence", Icons.Default.Terminal) {
        devices.forEach { device ->
            DeviceIntelligenceItem(device, onRevoke)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        }
        if (devices.isEmpty()) {
            Text("No active bindings detected.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun DeviceIntelligenceItem(
    device: DeviceDto,
    onRevoke: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.deviceModel ?: device.deviceId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("Status: ${device.status}", style = MaterialTheme.typography.labelSmall, color = if(device.status == "active") Color(0xFF4CAF50) else Color(0xFFF44336))
            }
            StatusBadge(device.status)
        }
        
        Spacer(Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Last Sync: ${device.lastActive?.take(16) ?: "Never"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            
            if (device.status == "active") {
                TextButton(
                    onClick = { onRevoke(device.deviceId) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("FORCE LOGOUT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ... HeaderSection, InfoSection, InfoRow, StatusBadge, RoleBadge remain same as before but improved ...
@Composable
fun HeaderSection(officer: OfficerDto) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Brush.linearGradient(colors = listOf(Color(0xFF6200EE), Color(0xFF03DAC6)))), contentAlignment = Alignment.Center) {
            Text((officer.profile?.name ?: officer.pno).take(1).uppercase(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(officer.profile?.name ?: officer.pno, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${officer.profile?.rank?.name ?: ""} | ${officer.pno}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(Modifier.height(12.dp))
        Row {
            StatusBadge(officer.profile?.serviceStatus ?: if (officer.isActive) "Active" else "Inactive")
            Spacer(Modifier.width(8.dp))
            RoleBadge(officer.role)
        }
    }
}

@Composable
fun InfoSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

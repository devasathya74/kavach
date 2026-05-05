package com.kavach.app.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.remote.dto.AdminOfficerDto
import com.kavach.app.data.remote.dto.SuspiciousSessionDto
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout  : () -> Unit,
    viewModel : AdminDashboardViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState  = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        containerColor = OfficialBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("COMMAND CONTROL PANEL", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Unit: PAC - All | Date: Today", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. LIVE STATUS STRIP ─────────────────────────────────
            LiveStatusStrip(uiState.officers)

            // ── 2. ALERTS PANEL (Critical Only) ──────────────────────
            AlertsPanel(uiState.suspiciousSessions)

            Text("OFFICER STATUS (LIVE)", style = MaterialTheme.typography.titleMedium, color = TextSecondary, fontWeight = FontWeight.Bold)

            // ── 3. USER TABLE ────────────────────────────────────────
            if (uiState.isLoading && uiState.officers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextPrimary)
                }
            } else if (uiState.error != null && uiState.officers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "", color = DangerRed)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.officers) { officer ->
                        UserTableRow(officer) { pno, action, reason ->
                            viewModel.performAction(pno, action, reason)
                        }
                    }
                }
            }
        }

        // Action loading overlay
        if (uiState.isActionLoading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SurfaceWhite) }
        }
    }
}

@Composable
private fun LiveStatusStrip(officers: List<AdminOfficerDto>) {
    val l1 = officers.count { it.disciplineScore <= 20 }
    val l2 = officers.count { it.disciplineScore in 21..40 }
    val l3 = officers.count { it.disciplineScore in 41..60 }
    val l4 = officers.count { it.disciplineScore in 61..100 }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatBox("L1 (Critical)", l1.toString(), DangerRed, Modifier.weight(1f))
        StatBox("L2 (Serious)", l2.toString(), Color(0xFFFF5722), Modifier.weight(1f))
        StatBox("L3 (Unreliable)", l3.toString(), Color(0xFFFFB300), Modifier.weight(1f))
        StatBox("L4 (Compliant)", l4.toString(), SuccessGreen, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AlertsPanel(sessions: List<SuspiciousSessionDto>) {
    if (sessions.isEmpty()) return
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DangerRed,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = "Alerts", tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("CRITICAL ALERTS", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            sessions.take(3).forEach { session ->
                Text("🚨 ${session.pno} - ${session.suspiciousReason}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun UserTableRow(officer: AdminOfficerDto, onActionClick: (String, String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    val score = officer.disciplineScore
    val (statusColor, levelText, statusText) = when {
        score <= 20 -> Triple(DangerRed, "🔴 L1", "CRITICAL")
        score <= 40 -> Triple(Color(0xFFFF5722), "🟠 L2", "SERIOUS")
        score <= 60 -> Triple(Color(0xFFFFB300), "🟡 L3", "UNRELIABLE")
        else -> Triple(SuccessGreen, "🟢 L4", "COMPLIANT")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority Bar Indicator
            Box(modifier = Modifier.width(4.dp).height(48.dp).background(statusColor))
            Spacer(Modifier.width(12.dp))

            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(OfficialBackground, shape = RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = TextSecondary)
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("${officer.pno} | ${officer.name}", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("${officer.unit} | Last Seen: 10:32 AM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("Score: $score", style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(levelText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextPrimary)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(text = { Text("📝 Add Character Roll Entry", color = TextPrimary) }, onClick = { expanded = false; onActionClick(officer.pno, "ADD_ROLL", "Admin Log") })
                    DropdownMenuItem(text = { Text("📱 Reset Device", color = TextPrimary) }, onClick = { expanded = false; onActionClick(officer.pno, "RESET_DEVICE", "Admin") })
                    DropdownMenuItem(text = { Text("🔄 Force Retraining", color = TextPrimary) }, onClick = { expanded = false; onActionClick(officer.pno, "RESET_TRAINING", "Admin") })
                }
            }
        }
    }
}

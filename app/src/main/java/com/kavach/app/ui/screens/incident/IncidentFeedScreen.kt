package com.kavach.app.ui.screens.incident

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.domain.model.Incident
import com.kavach.app.domain.model.IncidentStatus
import com.kavach.app.utils.OperationalUiState
import com.kavach.app.utils.OperationalActionState
import com.kavach.app.ui.theme.DangerRed
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.NavyBlueDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentFeedScreen(
    onBack: () -> Unit,
    viewModel: IncidentFeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Effect for Action Feedback
    LaunchedEffect(state.actionState) {
        when (val action = state.actionState) {
            is OperationalActionState.Success -> {
                snackbarHostState.showSnackbar(action.message)
                viewModel.clearActionState()
            }
            is OperationalActionState.Error -> {
                snackbarHostState.showSnackbar("Error: ${action.message}")
                viewModel.clearActionState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("INCIDENT FEED", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Operational Awareness Layer", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadIncidents(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Severity Filters
            SeverityFilterRow(
                selectedSeverity = state.selectedSeverity,
                onSeveritySelected = { viewModel.setSeverityFilter(it) }
            )

            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GoldenYellow)
            }

            when (val uiState = state.uiState) {
                is OperationalUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldenYellow)
                    }
                }
                is OperationalUiState.Error -> {
                    ErrorState(message = uiState.message, onRetry = { viewModel.loadIncidents(true) })
                }
                is OperationalUiState.Success -> {
                    if (uiState.data.isEmpty()) {
                        EmptyState("कोई घटना नहीं मिली")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.data, key = { it.localId }) { incident ->
                                IncidentCard(incident)
                            }
                        }
                    }
                }
                is OperationalUiState.Idle -> {
                    // Handled by VM init
                }
            }
        }
    }
}

@Composable
fun SeverityFilterRow(
    selectedSeverity: String?,
    onSeveritySelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedSeverity == null,
            onClick = { onSeveritySelected(null) },
            label = { Text("सभी") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = GoldenYellow,
                selectedLabelColor = NavyBlueDark,
                containerColor = Color.White.copy(alpha = 0.05f),
                labelColor = Color.White.copy(alpha = 0.6f)
            ),
            border = null
        )
        FilterChip(
            selected = selectedSeverity == "HIGH",
            onClick = { onSeveritySelected("HIGH") },
            label = { Text("गंभीर (High)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = DangerRed,
                selectedLabelColor = Color.White
            )
        )
        FilterChip(
            selected = selectedSeverity == "MEDIUM",
            onClick = { onSeveritySelected("MEDIUM") },
            label = { Text("मध्यम") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFF59E0B),
                selectedLabelColor = Color.White
            )
        )
    }
}

@Composable
fun IncidentCard(incident: Incident) {
    val severityColor = when (incident.severity) {
        "HIGH" -> DangerRed
        "MEDIUM" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val syncIcon = when (incident.status) {
        IncidentStatus.Draft -> Icons.Default.Edit
        IncidentStatus.PendingSync -> Icons.Default.Schedule
        IncidentStatus.Syncing -> Icons.Default.Sync
        IncidentStatus.Active -> Icons.Default.CheckCircle
        IncidentStatus.Failed -> Icons.Default.Error
        else -> Icons.Default.CloudQueue
    }

    val syncColor = when (incident.status) {
        IncidentStatus.Active -> Color(0xFF10B981)
        IncidentStatus.Failed -> DangerRed
        IncidentStatus.Syncing -> GoldenYellow
        else -> Color.White.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, syncColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(syncIcon, contentDescription = null, tint = syncColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = incident.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                
                Surface(
                    color = severityColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, severityColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = incident.severity,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = incident.summary,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(incident.occurredAt)),
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (incident.status == IncidentStatus.Syncing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = GoldenYellow)
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading...", color = GoldenYellow, style = MaterialTheme.typography.labelSmall)
                    }
                } else if (incident.status == IncidentStatus.Failed) {
                    Text("Sync Failed - Tap to retry", color = DangerRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = DangerRed, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow)) {
            Text("Retry Sync", color = NavyBlueDark)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Inbox, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
    }
}

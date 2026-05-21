package com.kavach.app.ui.screens.pilot.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.domain.model.DeviceStatus
import com.kavach.app.utils.OperationalActionState
import com.kavach.app.utils.OperationalUiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCenterScreen(
    viewModel: DeviceCenterViewModel = hiltViewModel(),
    onBack: () -> Unit
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
                        Text("DEVICE FLEET CENTER", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Operational Awareness Layer", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // Search Bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.onSearchChange(it) }
            )

            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (val uiState = state.uiState) {
                is OperationalUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is OperationalUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️ Sync Failed", fontWeight = FontWeight.Bold)
                            Text(uiState.message, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Button(onClick = { viewModel.onRefresh() }, modifier = Modifier.padding(top = 16.dp)) {
                                Text("Retry Sync")
                            }
                        }
                    }
                }
                is OperationalUiState.Success -> {
                    if (uiState.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No devices matching criteria.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.data, key = { it.id }) { device ->
                                DeviceCard(
                                    device = device,
                                    onRevoke = { viewModel.revokeDevice(device.officerId, device.deviceId) },
                                    onBlock = { viewModel.blockDevice(device.officerId, device.deviceId, "Operational Risk") }
                                )
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
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search Device ID, Model...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.3f)
        )
    )
}

@Composable
fun DeviceCard(
    device: DeviceListItemUiModel,
    onRevoke: () -> Unit,
    onBlock: () -> Unit
) {
    val statusColor = when (device.status) {
        DeviceStatus.Online -> Color(0xFF4ADE80)
        DeviceStatus.Degraded -> Color(0xFFFACC15)
        DeviceStatus.Stale -> Color(0xFFFB923C)
        DeviceStatus.Offline -> Color(0xFFF87171)
        DeviceStatus.Blocked -> Color(0xFF94A3B8)
        DeviceStatus.Unregistered -> Color(0xFF60A5FA)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if(device.status == DeviceStatus.Blocked) Icons.Default.Block else Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = statusColor
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text(device.deviceId, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        device.displayStatus.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IntelligenceMetric("Trust Score", "${device.trustScore.toInt()}%", if(device.trustScore > 80) Color(0xFF4ADE80) else Color(0xFFF87171))
                IntelligenceMetric("Integrity", device.integrityLevel, if(device.integrityLevel == "SECURE") Color(0xFF4ADE80) else Color(0xFFFACC15))
                IntelligenceMetric("Heartbeat", device.lastHeartbeatAt?.let { formatTime(it) } ?: "NEVER", Color.Gray)
            }
            
            Spacer(Modifier.height(20.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBlock,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Text("BLOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onRevoke,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("REVOKE & LOGOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
fun IntelligenceMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}



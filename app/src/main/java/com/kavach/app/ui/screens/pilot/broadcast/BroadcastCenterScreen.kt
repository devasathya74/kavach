package com.kavach.app.ui.screens.pilot.broadcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kavach.app.data.local.entity.BroadcastWithStats
import com.kavach.app.ui.screens.broadcast.BroadcastFilter
import com.kavach.app.ui.screens.broadcast.BroadcastViewModel
import com.kavach.app.ui.theme.DangerRed
import com.kavach.app.utils.OperationalUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastCenterScreen(
    viewModel: BroadcastViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onCreateBroadcast: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Broadcast Terminal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateBroadcast,
                icon = { Icon(Icons.Default.Campaign, null) },
                text = { Text("New Broadcast") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FilterRow(
                selected = state.selectedFilter,
                onSelect = { viewModel.setFilter(it) }
            )
            
            Box(modifier = Modifier.fillMaxSize()) {
                when (val uiState = state.uiState) {
                    is OperationalUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is OperationalUiState.Success -> {
                        BroadcastFeed(
                            broadcasts = uiState.data,
                            onAcknowledge = { viewModel.acknowledge(it) }
                        )
                    }
                    is OperationalUiState.Error -> {
                        Text(uiState.message, modifier = Modifier.align(Alignment.Center), color = DangerRed)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun FilterRow(
    selected: BroadcastFilter,
    onSelect: (BroadcastFilter) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selected.ordinal,
        edgePadding = 16.dp,
        divider = {},
        indicator = {}
    ) {
        BroadcastFilter.values().forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.name) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun BroadcastFeed(
    broadcasts: List<BroadcastWithStats>,
    onAcknowledge: (String) -> Unit
) {
    if (broadcasts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No broadcasts found", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(broadcasts, key = { it.broadcast.localId }) { item ->
                BroadcastCard(
                    item = item,
                    onAcknowledge = { onAcknowledge(item.broadcast.serverId ?: item.broadcast.localId) }
                )
            }
        }
    }
}

@Composable
fun BroadcastCard(
    item: BroadcastWithStats,
    onAcknowledge: () -> Unit
) {
    val broadcast = item.broadcast
    val priorityColor = when (broadcast.priority) {
        "HIGH" -> DangerRed
        "NORMAL" -> Color(0xFFF59E0B)
        else -> Color(0xFF60A5FA)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = broadcast.priority,
                    style = MaterialTheme.typography.labelSmall,
                    color = priorityColor,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = broadcast.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = broadcast.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = broadcast.content, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ── Delivery Intelligence ──────────────────────────
            DeliveryStatsRow(item)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "From: ${broadcast.senderName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                if (item.ackCount == 0) {
                    Button(onClick = onAcknowledge) {
                        Text("Acknowledge")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DoneAll, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Acknowledged", color = Color(0xFF10B981), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DeliveryStatsRow(item: BroadcastWithStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Gray.copy(alpha = 0.05f), MaterialTheme.shapes.small)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(label = "Sent", count = item.deliveredCount, total = item.totalRecipients)
        StatItem(label = "Read", count = item.readCount, total = item.totalRecipients)
        StatItem(label = "Ack", count = item.ackCount, total = item.totalRecipients)
        if (item.failedCount > 0) {
            StatItem(label = "Failed", count = item.failedCount, total = item.totalRecipients, color = DangerRed)
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, total: Int, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            text = "$count/$total",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

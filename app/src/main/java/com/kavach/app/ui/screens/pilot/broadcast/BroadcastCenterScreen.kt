package com.kavach.app.ui.screens.pilot.broadcast

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.remote.dto.broadcast.BroadcastDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastCenterScreen(
    onBack: () -> Unit,
    onCreateBroadcast: () -> Unit,
    viewModel: BroadcastCenterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("COMMAND BROADCAST CENTER", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadBroadcasts(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateBroadcast,
                containerColor = com.kavach.app.ui.theme.GoldenYellow,
                contentColor = com.kavach.app.ui.theme.NavyBlueDark,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Broadcast")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (state.isLoading && state.broadcasts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.broadcasts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active broadcasts.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.broadcasts) { broadcast ->
                        BroadcastItem(
                            broadcast = broadcast,
                            onAck = { viewModel.acknowledge(broadcast.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BroadcastItem(broadcast: BroadcastDto, onAck: () -> Unit) {
    val priorityColor = when (broadcast.priority) {
        "CRITICAL" -> Color(0xFFF44336)
        "URGENT" -> Color(0xFFFF9800)
        "IMPORTANT" -> Color(0xFF2196F3)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(priorityColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = priorityColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(broadcast.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text("By: ${broadcast.actorName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Surface(color = priorityColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Text(broadcast.priority, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = priorityColor)
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text(broadcast.content, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text("${broadcast.ackCount} Acknowledgments", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Button(
                    onClick = onAck,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("ACKNOWLEDGE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

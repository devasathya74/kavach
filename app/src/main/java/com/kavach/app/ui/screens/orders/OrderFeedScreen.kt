package com.kavach.app.ui.screens.orders

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
import com.kavach.app.domain.model.Order
import com.kavach.app.domain.model.OrderStatus
import com.kavach.app.ui.theme.DangerRed
import com.kavach.app.utils.OperationalActionState
import com.kavach.app.utils.OperationalUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFeedScreen(
    viewModel: OrderFeedViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionState) {
        if (state.actionState is OperationalActionState.Success) {
            snackbarHostState.showSnackbar((state.actionState as OperationalActionState.Success).message)
            viewModel.clearActionState()
        } else if (state.actionState is OperationalActionState.Error) {
            snackbarHostState.showSnackbar((state.actionState as OperationalActionState.Error).message)
            viewModel.clearActionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Command Orders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshOrders() }) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val uiState = state.uiState) {
                is OperationalUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is OperationalUiState.Success -> {
                    OrderList(
                        orders = uiState.data,
                        onAcknowledge = { viewModel.acknowledgeOrder(it) }
                    )
                }
                is OperationalUiState.Error -> {
                    ErrorState(message = uiState.message, onRetry = { viewModel.refreshOrders() })
                }
                else -> {}
            }
        }
    }
}

@Composable
fun OrderList(
    orders: List<Order>,
    onAcknowledge: (String) -> Unit
) {
    if (orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active orders", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(orders, key = { it.id }) { order ->
                FeedOrderCard(order = order, onAcknowledge = { onAcknowledge(order.id) })
            }
        }
    }
}

@Composable
fun FeedOrderCard(
    order: Order,
    onAcknowledge: () -> Unit
) {
    val statusColor = when (order.status) {
        OrderStatus.Pending -> DangerRed
        OrderStatus.Acknowledged -> Color(0xFF10B981)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.type,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = java.time.Instant.ofEpochMilli(order.issuedAt).toString().take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = order.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = order.content, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "By: ${order.issuedBy}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                if (order.status == OrderStatus.Pending) {
                    Button(
                        onClick = onAcknowledge,
                        colors = ButtonDefaults.buttonColors(containerColor = statusColor)
                    ) {
                        Text("Acknowledge")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (order.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = statusColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Syncing...", color = statusColor, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Acknowledged", color = statusColor, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = DangerRed, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

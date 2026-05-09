package com.kavach.app.ui.screens.pilot.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.remote.dto.system.DraftChangeDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalListScreen(
    viewModel: ApprovalListViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PENDING RATIFICATIONS", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.pendingChanges.isEmpty()) {
                EmptyApprovals(onRefresh = { viewModel.loadPendingChanges() })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.pendingChanges) { change ->
                        ApprovalCard(
                            change = change,
                            isProcessing = state.processingId == change.id,
                            isSelfRequest = change.targetPno == state.currentPno || change.actorName?.contains(state.currentPno) == true, // Simple check based on name/pno
                            onApprove = { viewModel.approveChange(change.id) },
                            onReject = { viewModel.rejectChange(change.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApprovalCard(
    change: DraftChangeDto,
    isProcessing: Boolean,
    isSelfRequest: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("RATIFICATION REQUEST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(Modifier.height(12.dp))
            Text("${change.targetName} (${change.targetPno})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Initiator: ${change.actorName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            if (isSelfRequest) {
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text("⚠️ Self-Approval Blocked", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            
            // Diff View
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.05f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(change.field.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(change.oldValue?.get("val")?.toString() ?: "N/A", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                        Text("  →  ", color = Color.Gray)
                        Text(change.newValue?.get("val")?.toString() ?: "N/A", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("REJECT")
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onApprove,
                    enabled = !isProcessing && !isSelfRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = if(isSelfRequest) Color.Gray else Color(0xFF4CAF50))
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("APPROVE")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyApprovals(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No pending ratifications.", color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }
    }
}

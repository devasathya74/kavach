package com.kavach.app.ui.screens.pilot.ota

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtaUpdateScreen(
    viewModel: OtaUpdateViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SYSTEM UPDATE CENTER", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
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
            } else if (state.latestUpdate == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                        Spacer(Modifier.height(16.dp))
                        Text("SYSTEM IS UP TO DATE", fontWeight = FontWeight.Bold)
                        Text("Current Version: v1.0.4", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            } else {
                UpdateDetailView(state, onDownload = { viewModel.startDownload() })
            }
        }
    }
}

@Composable
fun UpdateDetailView(state: OtaUpdateState, onDownload: () -> Unit) {
    val update = state.latestUpdate!!
    
    Column(modifier = Modifier.padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Version ${update.versionName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Build ${update.versionCode} • Released ${update.releasedAt?.take(10) ?: "Just now"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CHANGELOG", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(update.changelog, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        if (update.isMandatory) {
            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CRITICAL: This update is mandatory for mission continuity.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        if (state.installStatus == "DOWNLOADING") {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(progress = state.downloadProgress, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                Spacer(Modifier.height(8.dp))
                Text("Downloading... ${(state.downloadProgress * 100).toInt()}%", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
            }
        } else if (state.installStatus == "READY") {
            Button(
                onClick = { /* Install logic */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("INSTALL NOW", fontWeight = FontWeight.ExtraBold)
            }
        } else {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("DOWNLOAD UPDATE", fontWeight = FontWeight.ExtraBold)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        Text("SHA256: ${update.sha256.take(24)}...", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
    }
}

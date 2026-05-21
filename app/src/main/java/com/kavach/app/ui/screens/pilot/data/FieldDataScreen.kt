package com.kavach.app.ui.screens.pilot.data

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
import com.kavach.app.data.remote.dto.v2.FieldDataDto
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDataScreen(
    viewModel: FieldDataViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MISSION DATA REPOSITORY", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDocuments(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (state.isLoading && state.documents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.documents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No mission data shared.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.documents) { doc ->
                        DataCard(doc)
                    }
                }
            }
        }
    }
}

@Composable
fun DataCard(doc: FieldDataDto) {
    val categoryColor = when (doc.category) {
        "EVIDENCE" -> Color(0xFFF44336)
        "INTEL" -> Color(0xFFFF9800)
        "MAP" -> Color(0xFF2196F3)
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
                Box(Modifier.size(40.dp).clip(CircleShape).background(categoryColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if(doc.category == "EVIDENCE") Icons.Default.CameraAlt else Icons.Default.InsertDriveFile, 
                        contentDescription = null, 
                        tint = categoryColor, 
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text("${doc.uploaderPno ?: "Unknown"} • ${doc.createdAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Surface(color = categoryColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Text(doc.category, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = categoryColor)
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text("URL: ${doc.fileUrl?.take(40) ?: "N/A"}...", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
            
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { /* Download logic */ },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DOWNLOAD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

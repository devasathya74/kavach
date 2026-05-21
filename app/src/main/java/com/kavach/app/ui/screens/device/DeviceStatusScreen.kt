package com.kavach.app.ui.screens.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStatusScreen(
    onBack: () -> Unit,
    viewModel: DeviceStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("डिवाइस स्थिति (Device Status)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlueDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Trust Score Indicator
            TrustIndicator(uiState.integrityLevel)

            Spacer(modifier = Modifier.height(32.dp))

            // Device Info List
            InfoCard(uiState)

            Spacer(modifier = Modifier.weight(1f))

            // No longer needed: Security Check button removed to eliminate security theatre
            /*
            if (uiState.isAttesting) {
                CircularProgressIndicator(color = GoldenYellow)
            } else {
                Button(
                    onClick = { viewModel.checkIntegrity() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = NavyBlueDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("सुरक्षा जांच चलाएं (Run Check)", color = NavyBlueDark, fontWeight = FontWeight.Bold)
                }
            }
            */
        }
    }
}

@Composable
fun TrustIndicator(level: String) {
    val (color, label, description) = when (level) {
        "MEETS_STRONG_INTEGRITY" -> Triple(SuccessGreen, "अत्यधिक सुरक्षित (Strong)", "डिवाइस हार्डवेयर-बैकड सुरक्षा मानकों को पूरा करता है।")
        "MEETS_DEVICE_INTEGRITY" -> Triple(SuccessGreen, "सुरक्षित (Device)", "डिवाइस मानक सुरक्षा मानकों को पूरा करता है।")
        "MEETS_BASIC_INTEGRITY" -> Triple(GoldenYellow, "सीमित (Basic)", "डिवाइस केवल बुनियादी सुरक्षा मानकों को पूरा करता है।")
        else -> Triple(DangerRed, "असुरक्षित (Unverified)", "डिवाइस सुरक्षा सत्यापन विफल रहा या संदिग्ध है।")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(color.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (color == SuccessGreen) Icons.Default.Shield else Icons.Default.GppMaybe,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 14.sp
        )
    }
}

@Composable
fun InfoCard(state: DeviceStatusUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusRow("डिवाइस ID", state.deviceId)
            Divider(color = Color.White.copy(alpha = 0.1f))
            
            val statusLabel = when (state.integrityLevel) {
                "MEETS_STRONG_INTEGRITY" -> "Secure (अत्यधिक सुरक्षित)"
                "MEETS_DEVICE_INTEGRITY" -> "Secure (सुरक्षित)"
                "MEETS_BASIC_INTEGRITY"  -> "Limited (सीमित)"
                else -> "Attention Required (असुरक्षित)"
            }
            StatusRow("सुरक्षा स्थिति (Status)", statusLabel)
            Divider(color = Color.White.copy(alpha = 0.1f))
            StatusRow("अंतिम जांच", if (state.lastCheck > 0) "अभी-अभी" else "लंबित")
            
            if (state.error != null) {
                Divider(color = Color.White.copy(alpha = 0.1f))
                Text(state.error, color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

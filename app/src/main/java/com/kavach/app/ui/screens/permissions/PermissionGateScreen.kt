package com.kavach.app.ui.screens.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.theme.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun PermissionGateScreen(
    onContinue: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.checkPermissions()
        viewModel.markPermissionsAsHandled()
        // We always allow them to continue to dashboard, 
        // but the app state will reflect limited capability if denied.
        onContinue()
    }

    if (uiState.hasRequiredPermissions) {
        LaunchedEffect(Unit) { onContinue() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AdminPanelSettings,
            contentDescription = null,
            tint = GoldenYellow,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            "मिशन तैयारी: अनुमति आवश्यक\n(Mission Readiness: Permissions Required)",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "KAVACH को फील्ड ऑपरेशन के दौरान साक्ष्य एकत्र करने और कमांड प्राप्त करने के लिए इन अनुमतियों की आवश्यकता है।",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(40.dp))

        PermissionRequirementItem(Icons.Default.CameraAlt, "Camera", "For capturing incident evidence and field reporting.")
        PermissionRequirementItem(Icons.Default.Mic, "Microphone", "For voice briefings and audio reports.")
        PermissionRequirementItem(Icons.Default.LocationOn, "Location", "For mission geotagging and personnel safety tracking.")
        PermissionRequirementItem(Icons.Default.NotificationsActive, "Notifications", "For instant command and control broadcasts.")
        PermissionRequirementItem(Icons.Default.BatteryChargingFull, "Battery Optimization", "Must be disabled to ensure reliable background sync and command delivery.")

        Spacer(Modifier.weight(1f))
        
        val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
        val isIgnoringBatteryOptimizations = remember { 
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        }

        if (!isIgnoringBatteryOptimizations) {
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("अनुकूलन अक्षम करें (Disable Optimization)", color = Color.White)
            }
        }

        Button(
            onClick = { launcher.launch(permissionsToRequest) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("अनुमति दें (Grant Permissions)", color = NavyBlueDark, fontWeight = FontWeight.Bold)
        }
        
        TextButton(onClick = { 
            viewModel.markPermissionsAsHandled()
            onContinue() 
        }) {
            Text("बाद में करें (सीमित परिचालन क्षमता)\n[Decide Later - Limited Operational Capability]", 
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PermissionRequirementItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

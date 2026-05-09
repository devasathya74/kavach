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
    
    var currentStep by remember { mutableIntStateOf(1) }

    // Step 1: Notifications
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { currentStep = 2 }

    // Step 2: Location
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { currentStep = 3 }

    // Step 3: Camera & Mic
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { currentStep = 4 }

    // Step 4: Storage
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { 
        viewModel.checkPermissions()
        viewModel.markPermissionsAsHandled()
        onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Default.AdminPanelSettings,
            contentDescription = null,
            tint = GoldenYellow,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "मिशन तैयारी: चरण ${currentStep}/4\n(Mission Readiness: Step ${currentStep}/4)",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        when (currentStep) {
            1 -> PermissionStep(
                icon = Icons.Default.NotificationsActive,
                title = "सूचनाएं (Notifications)",
                desc = "कमांड और कंट्रोल ब्रॉडकास्ट प्राप्त करने के लिए।",
                buttonText = "अनुमति दें (Grant)",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        currentStep = 2
                    }
                }
            )
            2 -> PermissionStep(
                icon = Icons.Default.LocationOn,
                title = "स्थान (Location)",
                desc = "मिशन जियोटैगिंग और कर्मियों की सुरक्षा ट्रैकिंग के लिए।",
                buttonText = "अनुमति दें (Grant)",
                onClick = {
                    locationLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            )
            3 -> PermissionStep(
                icon = Icons.Default.CameraAlt,
                title = "कैमरा और माइक्रोफोन",
                desc = "साक्ष्य एकत्र करने और ब्रीफिंग रिकॉर्ड करने के लिए।",
                buttonText = "अनुमति दें (Grant)",
                onClick = {
                    mediaLauncher.launch(arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ))
                }
            )
            4 -> PermissionStep(
                icon = Icons.Default.Storage,
                title = "स्टोरेज (Storage)",
                desc = "दस्तावेजों और साक्ष्यों को सुरक्षित रखने के लिए।",
                buttonText = "अंतिम अनुमति (Final Grant)",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        storageLauncher.launch(arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO
                        ))
                    } else {
                        storageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    }
                }
            )
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = { 
            onContinue() 
        }) {
            Text("अभी छोड़ें (Skip for now)", color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    desc: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(100.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text(desc, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(buttonText, color = NavyBlueDark, fontWeight = FontWeight.Bold)
        }
    }
}

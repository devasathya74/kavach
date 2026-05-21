package com.kavach.app.ui.screens.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.theme.*
import timber.log.Timber

@Composable
fun PermissionGateScreen(
    onContinue: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check permissions when user returns to app (e.g., from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Effect: If all permissions are granted, auto-continue
    LaunchedEffect(uiState.hasRequiredPermissions) {
        if (uiState.hasRequiredPermissions) {
            Timber.d("All permissions granted. Transitioning to next screen.")
            viewModel.markPermissionsAsHandled()
            onContinue()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            viewModel.markPermissionsAsHandled()
            onContinue()
        } else {
            // Some denied. checkPermissions will update UI state
            viewModel.checkPermissions()
        }
    }

    val mandatoryPermissions = remember {
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(GoldenYellow.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = GoldenYellow,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        
        Text(
            "मिशन अनुमति गेट\n(Mission Permission Gate)",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            "सुरक्षित संचालन के लिए निम्नलिखित अनुमतियां अनिवार्य हैं।\n(The following permissions are mandatory for secure operations.)",
            color = OnSurfaceMid,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(40.dp))

        // Permission List
        PermissionItem(
            icon = Icons.Default.NotificationsActive,
            title = "सूचनाएं (Notifications)",
            desc = "कमांड अलर्ट और रीयल-टाइम अपडेट के लिए।"
        )
        
        PermissionItem(
            icon = Icons.Default.LocationOn,
            title = "लोकेशन (Location)",
            desc = "कर्मियों की सुरक्षा और जियो-फेन्सिंग के लिए।"
        )
        
        PermissionItem(
            icon = Icons.Default.CameraAlt,
            title = "कैमरा और माइक्रोफोन",
            desc = "साक्ष्य एकत्र करने और सुरक्षित संचार के लिए।"
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(40.dp))

        if (uiState.isPermanentlyDenied) {
            // Show Open Settings button
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "कुछ अनुमतियां स्थायी रूप से अस्वीकार कर दी गई हैं। कृपया सेटिंग्स में जाकर उन्हें सक्षम करें।",
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("सेटिंग्स खोलें (Open Settings)", color = NavyBlueDark, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { launcher.launch(mandatoryPermissions) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("अनुमति दें (Grant All Permissions)", color = NavyBlueDark, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            "सुरक्षा प्रोटोकॉल: अनुमतियों के बिना आगे बढ़ना वर्जित है।",
            color = OnSurfaceMid.copy(alpha = 0.5f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GoldenYellow,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(desc, color = OnSurfaceMid, fontSize = 12.sp)
        }
    }
}

package com.kavach.app.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.core.security.BiometricAuthManager
import com.kavach.app.ui.components.TacticalKeypad
import com.kavach.app.ui.theme.*

@Composable
fun SecureAccessScreen(
    biometricManager: BiometricAuthManager,
    viewModel: SecureAccessViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, null, tint = GoldenYellow, modifier = Modifier.size(64.dp))
            
            Spacer(Modifier.height(24.dp))
            
            Text("Tactical Access Required", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("सत्र अनलॉक करने के लिए PIN दर्ज करें", color = OnSurfaceMid, fontSize = 14.sp)

            Spacer(Modifier.height(48.dp))

            // PIN Indicator
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (index < state.pin.length) GoldenYellow else Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            if (state.error != null) {
                Text(state.error!!, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }

            TacticalKeypad(
                onDigit = { viewModel.onPinInput(it) },
                onBackspace = { viewModel.onBackspace() }
            )

        }
        
        // Institutional Tag
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Text(
                "KAVACH TACTICAL ENCRYPTION ACTIVE",
                color = OnSurfaceLow,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }
    }
}

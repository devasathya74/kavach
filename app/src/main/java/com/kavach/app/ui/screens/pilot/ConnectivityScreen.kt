package com.kavach.app.ui.screens.pilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.OnSurface
import com.kavach.app.ui.theme.OnSurfaceMid
import com.kavach.app.ui.theme.ColorSuccess
import com.kavach.app.ui.theme.ColorError
import com.kavach.app.ui.theme.Surface1

@Composable
fun ConnectivityScreen(
    viewModel: ConnectivityViewModel = hiltViewModel(),
    onSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete && !state.hasError) {
            onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Connectivity",
                tint = GoldenYellow,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SYSTEM CONNECTIVITY CHECK",
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Verifying secure uplink to Kavach server...",
                color = OnSurfaceMid,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface1),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DiagnosticRow(
                        label = "API Uplink Server",
                        status = when (state.apiReachable) {
                            true -> "REACHABLE"
                            false -> "UNREACHABLE"
                            null -> "PENDING"
                        },
                        isOk = state.apiReachable
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    DiagnosticRow(
                        label = "Local Data Vault",
                        status = when (state.dbHealthy) {
                            true -> "SECURE"
                            false -> "DEGRADED"
                            null -> "PENDING"
                        },
                        isOk = state.dbHealthy
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (state.hasError) {
                Text(
                    text = state.errorMessage ?: "Could not establish secure link.",
                    color = ColorError,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.runDiagnostics() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = NavyBlueDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETRY TELEMETRY LINK",
                        color = NavyBlueDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else if (!state.isComplete) {
                CircularProgressIndicator(color = GoldenYellow)
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, status: String, isOk: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status,
                color = when (isOk) {
                    true -> ColorSuccess
                    false -> ColorError
                    null -> OnSurfaceMid
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = when (isOk) {
                    true -> Icons.Default.CheckCircle
                    false -> Icons.Default.Error
                    null -> Icons.Default.Refresh
                },
                contentDescription = null,
                tint = when (isOk) {
                    true -> ColorSuccess
                    false -> ColorError
                    null -> OnSurfaceMid
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

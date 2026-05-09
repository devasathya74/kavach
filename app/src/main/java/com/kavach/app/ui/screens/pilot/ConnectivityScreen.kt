package com.kavach.app.ui.screens.pilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.NavyBlueDark
import kotlinx.coroutines.delay

@Composable
fun ConnectivityScreen(
    onSuccess: () -> Unit,
    viewModel: ConnectivityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Fast fail / fast success
    LaunchedEffect(state.isComplete, state.hasError) {
        if (state.isComplete && !state.hasError) {
            // Add a tiny delay so the user sees the green checks before flashing away
            delay(500)
            onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = "KAVACH SYSTEM CHECK",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            DiagnosticRow("API Connection (Tunnel)", state.apiReachable)
            
            if (!com.kavach.app.KavachConfig.PILOT_MODE) {
                DiagnosticRow("Database Health", state.dbHealthy)
                DiagnosticRow("Cache Integrity", state.cacheHealthy)
                DiagnosticRow("App Version Compatibility", state.versionCompatible)
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (state.hasError && state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (com.kavach.app.KavachConfig.PILOT_MODE) "कनेक्टिविटी समस्या (OFFLINE)" else "SYSTEM HALTED",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.runDiagnostics() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RETRY", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                }
            } else if (!state.isComplete) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun DiagnosticRow(name: String, status: Boolean?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val icon = when (status) {
            true -> Icons.Default.CheckCircle
            false -> Icons.Default.Error
            null -> Icons.Default.HourglassEmpty
        }
        val tint = when (status) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

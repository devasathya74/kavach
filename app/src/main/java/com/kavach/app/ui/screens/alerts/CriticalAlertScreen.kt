package com.kavach.app.ui.screens.alerts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun CriticalAlertScreen(
    title: String,
    content: String,
    onAcknowledge: (Long) -> Unit
) {
    // 1. Force screen lock (Disable Back Button)
    BackHandler(enabled = true) {
        // Do nothing. Force acknowledgment.
    }

    var canAck by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    val readTimeStart = remember { System.currentTimeMillis() }

    // 2. Minimum Read Time (e.g. 5 seconds) before allowing interaction
    LaunchedEffect(Unit) {
        delay(5000)
        canAck = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C)), // Deep Red
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Critical",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "आपात आदेश\n(CRITICAL ALERT)",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Surface(
                color = Color.Black.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Hold to Acknowledge Button
            if (canAck) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            if (holdProgress > 0f) Color(0xFFD32F2F) else Color.White,
                            shape = MaterialTheme.shapes.large
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val startHold = System.currentTimeMillis()
                                    var isHolding = true
                                    
                                    // Simulated progress visual
                                    tryAwaitRelease()
                                    isHolding = false
                                    val duration = System.currentTimeMillis() - startHold

                                    if (duration > 2000) { // 2 seconds hold
                                        val readDurationSecs = (System.currentTimeMillis() - readTimeStart) / 1000
                                        onAcknowledge(readDurationSecs)
                                    } else {
                                        holdProgress = 0f // Reset
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LONG PRESS TO ACKNOWLEDGE",
                        color = if (holdProgress > 0f) Color.White else Color(0xFFB71C1C),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                CircularProgressIndicator(color = Color.White)
                Text("कृपया प्रतीक्षा करें...", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

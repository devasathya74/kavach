package com.kavach.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@Composable
fun WatermarkOverlay(pno: String) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Pulsing alpha
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Change position every 5 seconds
    LaunchedEffect(size) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        while (true) {
            currentTime = System.currentTimeMillis()
            offsetX = Random.nextFloat() * (size.width * 0.6f)
            offsetY = Random.nextFloat() * (size.height * 0.8f)
            delay(5000L)
        }
    }

    val density = LocalDensity.current
    val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
    ) {
        if (size != IntSize.Zero) {
            Text(
                text = "$pno\n${sdf.format(Date(currentTime))}",
                color = Color.Gray,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(
                        x = with(density) { offsetX.toDp() },
                        y = with(density) { offsetY.toDp() }
                    )
                    .alpha(alpha)
                    .rotate(-30f)
            )
        }
    }
}

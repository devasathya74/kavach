package com.kavach.app.ui.screens.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * SplashScreen — Secure boot sequence.
 *
 * Shows a procedural initialization sequence with status steps,
 * then calls [onBootComplete] when all checks pass.
 *
 * States: SECURE → onBootComplete | COMPROMISED → onCompromised
 */
@Composable
fun SplashScreen(
    onBootComplete  : () -> Unit,
    onCompromised   : () -> Unit = {}
) {
    // Boot steps shown sequentially
    val bootSteps = listOf(
        "ROOT DETECTION           ... OK",
        "TOKEN VALIDATION         ... OK",
        "SECURE STORAGE           ... MOUNTED",
        "WEBSOCKET PRECHECK       ... READY",
        "API HEALTH PING          ... ALIVE",
        "KERNEL SEAL              ... VERIFIED",
        "ENVIRONMENT SEALED       ... SECURE"
    )

    var visibleSteps  by remember { mutableIntStateOf(0) }
    var progress      by remember { mutableFloatStateOf(0f) }
    var bootStatus    by remember { mutableStateOf("INITIALIZING") } // INITIALIZING | SECURE | WARNING | COMPROMISED
    var showContinue  by remember { mutableStateOf(false) }

    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800),
        label = "logo"
    )

    val progressAnim by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    // Simulate sequential boot sequence
    LaunchedEffect(Unit) {
        delay(400)
        repeat(bootSteps.size) { index ->
            visibleSteps = index + 1
            progress = (index + 1).toFloat() / bootSteps.size
            delay(350)
        }
        delay(300)
        bootStatus = "SECURE"
        delay(600)
        showContinue = true
        delay(800)
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030D1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Logo ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .alpha(logoAlpha)
                    .background(
                        color = GoldenYellow.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Shield,
                    contentDescription = null,
                    tint               = GoldenYellow,
                    modifier           = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text       = "KAVACH",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Black,
                color      = Color.White,
                letterSpacing = 8.sp
            )

            Text(
                text  = "TACTICAL COMMAND FRAMEWORK",
                fontSize = 10.sp,
                color = GoldenYellow.copy(alpha = 0.7f),
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(48.dp))

            // ── Boot Log Panel ────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = Color(0xFF0A1628),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text      = "[ SYSTEM BOOT LOG ]",
                        fontSize  = 10.sp,
                        color     = GoldenYellow.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        modifier  = Modifier.padding(bottom = 12.dp)
                    )

                    bootSteps.take(visibleSteps).forEachIndexed { index, step ->
                        AnimatedVisibility(
                            visible = index < visibleSteps,
                            enter   = fadeIn(tween(200)) + slideInVertically(tween(200))
                        ) {
                            Text(
                                text      = "> $step",
                                fontSize  = 11.sp,
                                color     = if (index == visibleSteps - 1 && bootStatus == "INITIALIZING")
                                                GoldenYellow
                                            else
                                                Color(0xFF5E9B6A),
                                fontFamily = FontFamily.Monospace,
                                modifier  = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Progress Bar ──────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = when (bootStatus) {
                            "SECURE"      -> "■ ENVIRONMENT SECURE"
                            "WARNING"     -> "▲ DEGRADED ENVIRONMENT"
                            "COMPROMISED" -> "✕ SECURITY COMPROMISED"
                            else          -> "● INITIALIZING SECURE KERNEL..."
                        },
                        fontSize = 10.sp,
                        color    = when (bootStatus) {
                            "SECURE"      -> SuccessGreen
                            "WARNING"     -> GoldenYellow
                            "COMPROMISED" -> DangerRed
                            else          -> Color.White.copy(alpha = 0.5f)
                        },
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text  = "${(progressAnim * 100).toInt()}%",
                        fontSize = 10.sp,
                        color    = GoldenYellow,
                        fontFamily = FontFamily.Monospace
                    )
                }

                LinearProgressIndicator(
                    progress     = { progressAnim },
                    modifier     = Modifier.fillMaxWidth().height(4.dp),
                    color        = when (bootStatus) {
                        "SECURE"      -> SuccessGreen
                        "WARNING"     -> GoldenYellow
                        "COMPROMISED" -> DangerRed
                        else          -> GoldenYellow
                    },
                    trackColor   = Color.White.copy(alpha = 0.08f)
                )
            }

            Spacer(Modifier.height(48.dp))

            // ── Footer ────────────────────────────────────────────
            Text(
                text      = "v2.1.0  ·  KERNEL-HARDENED  ·  CLASSIFIED",
                fontSize  = 9.sp,
                color     = Color.White.copy(alpha = 0.2f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

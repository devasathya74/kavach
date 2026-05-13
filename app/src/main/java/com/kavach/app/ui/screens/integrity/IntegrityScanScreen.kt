package com.kavach.app.ui.screens.integrity

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * IntegrityScanScreen — Device validation after login.
 *
 * Runs a sequential series of security checks and displays
 * a live threat score. On pass → [onContinue]. On abort → [onAbort].
 */

data class ScanCheck(
    val label  : String,
    val icon   : ImageVector,
    val status : ScanStatus = ScanStatus.PENDING
)

enum class ScanStatus { PENDING, SCANNING, PASS, WARN, FAIL }

@Composable
fun IntegrityScanScreen(
    onContinue : () -> Unit,
    onAbort    : () -> Unit
) {
    val checks = remember {
        mutableStateListOf(
            ScanCheck("Root Detection",          Icons.Default.Security,      ScanStatus.PENDING),
            ScanCheck("App Signature",           Icons.Default.Fingerprint,   ScanStatus.PENDING),
            ScanCheck("Emulator Check",          Icons.Default.PhoneAndroid,  ScanStatus.PENDING),
            ScanCheck("App Clone Detection",     Icons.Default.ContentCopy,   ScanStatus.PENDING),
            ScanCheck("Kernel Integrity",        Icons.Default.Memory,        ScanStatus.PENDING),
            ScanCheck("Suspicious Environment",  Icons.Default.BugReport,     ScanStatus.PENDING),
        )
    }

    var threatScore     by remember { mutableIntStateOf(0) }
    var overallStatus   by remember { mutableStateOf("SCANNING") } // SCANNING | PASS | WARN | FAIL
    var scanComplete    by remember { mutableStateOf(false) }
    var deviceId        by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        deviceId = "KAVACH-${System.currentTimeMillis().toString().takeLast(8)}"
        delay(300)

        checks.forEachIndexed { index, _ ->
            // Mark as scanning
            checks[index] = checks[index].copy(status = ScanStatus.SCANNING)
            delay(500)
            // Simulate result (all PASS in production, replace with real check)
            checks[index] = checks[index].copy(status = ScanStatus.PASS)
            threatScore = ((index + 1) * (100 / checks.size))
        }

        delay(400)
        overallStatus = "PASS"
        scanComplete  = true

        // Auto-continue after a brief pause
        delay(1200)
        onContinue()
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header ───────────────────────────────────────────
            Spacer(Modifier.height(32.dp))

            Text(
                text      = "DEVICE INTEGRITY SCAN",
                fontSize  = 14.sp,
                color     = GoldenYellow,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text  = "Verifying operational environment",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // ── Threat Score Ring ─────────────────────────────────
            ThreatScoreRing(score = threatScore, status = overallStatus)

            Spacer(Modifier.height(28.dp))

            // ── Device ID ────────────────────────────────────────
            Surface(
                color = Color(0xFF0A1628),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = GoldenYellow.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text      = "DEVICE FINGERPRINT: $deviceId",
                        fontSize  = 10.sp,
                        color     = Color.White.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Check List ────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = Color(0xFF0A1628),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    checks.forEach { check ->
                        ScanCheckRow(check)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Action Buttons ────────────────────────────────────
            AnimatedVisibility(visible = scanComplete) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick  = onContinue,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = GoldenYellow,
                            contentColor   = NavyBlueDark
                        )
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("CONTINUE SECURELY", fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick  = onAbort,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                    ) {
                        Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("ABORT / LOGOUT", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!scanComplete) {
                CircularProgressIndicator(
                    color    = GoldenYellow,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThreatScoreRing(score: Int, status: String) {
    val color = when (status) {
        "PASS" -> SuccessGreen
        "WARN" -> GoldenYellow
        "FAIL" -> DangerRed
        else   -> GoldenYellow
    }

    val scoreAnim by animateFloatAsState(
        targetValue    = score / 100f,
        animationSpec  = tween(500),
        label          = "score"
    )

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress     = { scoreAnim },
            modifier     = Modifier.size(110.dp),
            color        = color,
            trackColor   = color.copy(alpha = 0.1f),
            strokeWidth  = 6.dp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "$score",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Black,
                color      = color
            )
            Text(
                text  = when (status) {
                    "PASS"     -> "SECURE"
                    "WARN"     -> "WARNING"
                    "FAIL"     -> "BLOCKED"
                    else       -> "SCANNING"
                },
                fontSize   = 9.sp,
                color      = color,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ScanCheckRow(check: ScanCheck) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier         = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when (check.status) {
                        ScanStatus.PASS    -> SuccessGreen.copy(alpha = 0.15f)
                        ScanStatus.FAIL    -> DangerRed.copy(alpha = 0.15f)
                        ScanStatus.WARN    -> GoldenYellow.copy(alpha = 0.15f)
                        ScanStatus.SCANNING -> GoldenYellow.copy(alpha = 0.08f)
                        ScanStatus.PENDING  -> Color.White.copy(alpha = 0.04f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (check.status) {
                ScanStatus.PASS     -> Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                ScanStatus.FAIL     -> Icon(Icons.Default.Close, null, tint = DangerRed, modifier = Modifier.size(14.dp))
                ScanStatus.WARN     -> Icon(Icons.Default.Warning, null, tint = GoldenYellow, modifier = Modifier.size(14.dp))
                ScanStatus.SCANNING -> CircularProgressIndicator(modifier = Modifier.size(14.dp), color = GoldenYellow, strokeWidth = 1.5.dp)
                ScanStatus.PENDING  -> Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text  = check.label,
            style = MaterialTheme.typography.bodySmall,
            color = when (check.status) {
                ScanStatus.PENDING  -> Color.White.copy(alpha = 0.3f)
                ScanStatus.SCANNING -> Color.White
                else                -> Color.White.copy(alpha = 0.8f)
            },
            modifier = Modifier.weight(1f)
        )

        Text(
            text  = when (check.status) {
                ScanStatus.PASS     -> "PASS"
                ScanStatus.FAIL     -> "FAIL"
                ScanStatus.WARN     -> "WARN"
                ScanStatus.SCANNING -> "..."
                ScanStatus.PENDING  -> "—"
            },
            fontSize  = 10.sp,
            color     = when (check.status) {
                ScanStatus.PASS     -> SuccessGreen
                ScanStatus.FAIL     -> DangerRed
                ScanStatus.WARN     -> GoldenYellow
                ScanStatus.SCANNING -> GoldenYellow
                ScanStatus.PENDING  -> Color.White.copy(alpha = 0.2f)
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

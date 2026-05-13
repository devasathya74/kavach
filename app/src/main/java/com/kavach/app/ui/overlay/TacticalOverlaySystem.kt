package com.kavach.app.ui.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kavach.app.core.events.SystemEvent
import com.kavach.app.core.security.ThreatBanner
import com.kavach.app.core.security.ThreatLevel
import com.kavach.app.core.security.threatBackgroundTint
import com.kavach.app.core.security.threatBorderModifier
import com.kavach.app.core.security.threatPulseAlpha
import com.kavach.app.core.security.toColor
import com.kavach.app.ui.tokens.TacticalSpacing
import com.kavach.app.ui.tokens.TacticalRadius
import com.kavach.app.ui.tokens.TacticalType
import com.kavach.app.ui.theme.*

/**
 * TacticalOverlaySystem — Global overlay layer.
 *
 * Wraps [content] and overlays system-level UI that:
 *   - Ignores navigation state
 *   - Appears globally above all screens
 *   - Overrides normal UX when activated
 *
 * Handles:
 *   - [CommandInterruptionOverlay] — Senanayak command override / lockdown
 *   - [ThreatBanner]              — Threat level indicator strip
 *   - [EmergencyBroadcastBanner]  — Time-critical broadcast notification
 *   - [UplinkDegradedBanner]      — Connectivity warning strip
 *
 * Usage: Wrap KavachNavHost content with this composable.
 */
@Composable
fun TacticalOverlaySystem(
    threatLevel         : ThreatLevel,
    pendingCommand      : SystemEvent.CommandOverride?,
    isLockdown          : Boolean,
    emergencyBroadcast  : SystemEvent.EmergencyBroadcast?,
    uplinkDegraded      : Boolean,
    onCommandAcknowledged : () -> Unit,
    onLockdownAcknowledged : () -> Unit,
    onBroadcastDismissed : () -> Unit,
    content             : @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Base Content (all screens) ────────────────────────
        content()

        // ── Threat Tint Layer (overlays content subtly) ───────
        if (threatLevel != ThreatLevel.SECURE) {
            val tint = threatBackgroundTint(threatLevel)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tint)
                    // Don't consume pointer events — transparent to interaction
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        enabled           = false,
                        onClick           = {}
                    )
            )
        }

        // ── Uplink Degraded Banner (top) ──────────────────────
        AnimatedVisibility(
            visible = uplinkDegraded,
            enter   = slideInVertically(tween(300)) { -it },
            exit    = slideOutVertically(tween(300)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            UplinkDegradedBanner()
        }

        // ── Emergency Broadcast Banner ────────────────────────
        AnimatedVisibility(
            visible  = emergencyBroadcast != null,
            enter    = slideInVertically(tween(400)) { -it } + fadeIn(tween(300)),
            exit     = slideOutVertically(tween(400)) { -it } + fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = if (uplinkDegraded) 28.dp else 0.dp)
        ) {
            if (emergencyBroadcast != null) {
                EmergencyBroadcastBanner(
                    broadcast = emergencyBroadcast,
                    onDismiss = onBroadcastDismissed
                )
            }
        }

        // ── CRITICAL: Command Interruption (full-screen modal) ──
        if (isLockdown) {
            LockdownModal(onAcknowledged = onLockdownAcknowledged)
        } else if (pendingCommand != null) {
            CommandInterruptionOverlay(
                command       = pendingCommand,
                onAcknowledge = onCommandAcknowledged
            )
        }
    }
}

// ── Command Interruption Modal ────────────────────────────────────────────────

/**
 * Full-screen modal for Senanayak command overrides.
 * Navigation is blocked; acknowledgment is mandatory.
 */
@Composable
fun CommandInterruptionOverlay(
    command       : SystemEvent.CommandOverride,
    onAcknowledge : () -> Unit
) {
    Dialog(
        onDismissRequest = { /* BLOCKED — mandatory ack */ },
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "cmd")
        val borderAlpha by infiniteTransition.animateFloat(
            initialValue  = 0.4f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label         = "border"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .threatBorderModifier(ThreatLevel.CRITICAL),
                color  = Color(0xFF0A1628),
                shape  = RoundedCornerShape(TacticalRadius.Dialog)
            ) {
                Column(
                    modifier  = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TacticalSpacing.Gap)
                ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GppMaybe, null, tint = DangerRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "COMMAND DIRECTIVE",
                            fontSize   = TacticalType.LabelBase,
                            color      = DangerRed,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    HorizontalDivider(color = DangerRed.copy(alpha = 0.3f))

                    Text(
                        text       = command.title,
                        fontSize   = TacticalType.TitleBase,
                        fontWeight = FontWeight.Black,
                        color      = Color.White,
                        textAlign  = TextAlign.Center
                    )

                    Surface(
                        color  = Color.White.copy(alpha = 0.04f),
                        shape  = RoundedCornerShape(TacticalRadius.Base)
                    ) {
                        Text(
                            text     = command.body,
                            fontSize = TacticalType.BodyBase,
                            color    = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(TacticalSpacing.Base),
                            textAlign = TextAlign.Start
                        )
                    }

                    Text(
                        text  = "ISSUED BY: ${command.issuedBy}",
                        fontSize = TacticalType.LabelSmall,
                        color = GoldenYellow.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(TacticalSpacing.Tight))

                    if (command.requiresAck) {
                        Button(
                            onClick  = onAcknowledge,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = GoldenYellow,
                                contentColor   = Color(0xFF030D1A)
                            ),
                            shape = RoundedCornerShape(TacticalRadius.Card)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ACKNOWLEDGE DIRECTIVE", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

// ── Lockdown Modal ────────────────────────────────────────────────────────────

@Composable
fun LockdownModal(onAcknowledged: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "lockdown")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label         = "alert_pulse"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE5000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TacticalSpacing.Gap)
            ) {
                Icon(
                    Icons.Default.LockClock,
                    contentDescription = null,
                    tint = DangerRed.copy(alpha = alpha),
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text       = "⚠ LOCKDOWN PROTOCOL ACTIVE",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Black,
                    color      = DangerRed.copy(alpha = alpha),
                    letterSpacing = 2.sp,
                    textAlign  = TextAlign.Center
                )
                Text(
                    text      = "ALL OPERATIONS SUSPENDED\nAWAITING CLEARANCE FROM COMMAND",
                    fontSize  = TacticalType.BodyBase,
                    color     = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(TacticalSpacing.Section))
                OutlinedButton(
                    onClick = onAcknowledged,
                    border  = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                ) {
                    Text("ACKNOWLEDGE LOCKDOWN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Emergency Broadcast Banner ────────────────────────────────────────────────

@Composable
fun EmergencyBroadcastBanner(
    broadcast : SystemEvent.EmergencyBroadcast,
    onDismiss : () -> Unit
) {
    val alpha = threatPulseAlpha(ThreatLevel.CRITICAL)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = DangerRed.copy(alpha = 0.92f)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(TacticalSpacing.Tight))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "EMERGENCY BROADCAST",
                    fontSize  = TacticalType.LabelSmall,
                    color     = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    broadcast.message,
                    fontSize  = TacticalType.BodySmall,
                    color     = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines  = 2
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Uplink Degraded Banner ────────────────────────────────────────────────────

@Composable
fun UplinkDegradedBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = Color(0xFFEA580C).copy(alpha = 0.9f)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "UPLINK DEGRADED — OPERATING IN LIMITED MODE",
                fontSize   = TacticalType.LabelSmall,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

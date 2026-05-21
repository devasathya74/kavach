package com.kavach.app.core.security

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ThreatEffects — Composable visual effects driven by ThreatLevel.
 *
 * Provides:
 *  - [threatBorderModifier]   — animated border that pulses with threat color
 *  - [threatBackgroundTint]   — subtle background color overlay
 *  - [threatPulseAlpha]       — animated alpha for pulsing elements
 *  - [threatBorderColor]      — current color for the given threat level
 */

/** Returns the Compose Color for a given ThreatLevel. */
fun ThreatLevel.toColor(): Color = Color(colorHex)

/** Animated pulsing border modifier — attach to any container. */
fun Modifier.threatBorderModifier(level: ThreatLevel, width: Dp = 1.5.dp): Modifier = composed {
    if (level == ThreatLevel.SECURE) return@composed this

    val color = level.toColor()

    val infiniteTransition = rememberInfiniteTransition(label = "threat_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue   = 0.2f,
        targetValue    = 0.9f,
        animationSpec  = infiniteRepeatable(
            animation   = tween(
                durationMillis = (60_000 / level.pulseBpm),
                easing         = FastOutSlowInEasing
            ),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    this.drawBehind {
        drawRect(
            color       = color.copy(alpha = alpha),
            style       = androidx.compose.ui.graphics.drawscope.Stroke(width = width.toPx())
        )
    }
}

/** Subtle background tint for threat-aware containers. */
@Composable
fun threatBackgroundTint(level: ThreatLevel): Color {
    val target = when (level) {
        ThreatLevel.SECURE      -> Color.Transparent
        ThreatLevel.WARNING     -> Color(0xFFF59E0B).copy(alpha = 0.04f)
        ThreatLevel.ELEVATED    -> Color(0xFFEA580C).copy(alpha = 0.06f)
        ThreatLevel.CRITICAL    -> Color(0xFFDC2626).copy(alpha = 0.09f)
        ThreatLevel.COMPROMISED -> Color(0xFF7F1D1D).copy(alpha = 0.14f)
    }
    return animateColorAsState(
        targetValue  = target,
        animationSpec = tween(600),
        label        = "bg_tint"
    ).value
}

/** Animated alpha value for pulsing threat indicators (e.g., status dot). */
@Composable
fun threatPulseAlpha(level: ThreatLevel): Float {
    if (level == ThreatLevel.SECURE) return 1f

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_alpha")
    return infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation   = tween(
                durationMillis = (60_000 / level.pulseBpm),
                easing         = LinearEasing
            ),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "pulse"
    ).value
}

/** Composable utility — renders a pulsing colored status dot. */
@Composable
fun ThreatStatusDot(
    level    : ThreatLevel,
    modifier : Modifier = Modifier.size(8.dp)
) {
    val alpha = threatPulseAlpha(level)
    Box(
        modifier = modifier.background(
            color = level.toColor().copy(alpha = alpha),
            shape = androidx.compose.foundation.shape.CircleShape
        )
    )
}

/** A thin banner across the top of a screen, visible at WARNING or above. */
@Composable
fun ThreatBanner(level: ThreatLevel) {
    if (level == ThreatLevel.SECURE) return

    val color = level.toColor()
    val alpha = threatPulseAlpha(level)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(color.copy(alpha = alpha))
    )
}

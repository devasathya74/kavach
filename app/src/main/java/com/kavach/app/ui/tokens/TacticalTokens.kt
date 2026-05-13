package com.kavach.app.ui.tokens

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * TacticalTokens — Centralized design system token objects.
 *
 * All screens must use these constants rather than ad-hoc values.
 * This prevents visual drift across the platform as it scales.
 *
 * Token categories:
 *   - TacticalSpacing    — layout gaps and padding
 *   - TacticalRadius     — corner radius values
 *   - TacticalBorders    — border width values
 *   - TacticalElevation  — shadow/elevation levels
 *   - TacticalType       — font sizes and weights
 */

object TacticalSpacing {
    val None    = 0.dp
    val Micro   = 4.dp
    val Tight   = 8.dp
    val Base    = 12.dp
    val Gap     = 16.dp
    val Section = 24.dp
    val Block   = 32.dp
    val Screen  = 48.dp
}

object TacticalRadius {
    val None    = 0.dp
    val Sharp   = 4.dp
    val Base    = 8.dp
    val Card    = 12.dp
    val Panel   = 16.dp
    val Dialog  = 20.dp
    val Pill    = 50.dp
}

object TacticalBorders {
    val Hair    = 0.5.dp
    val Thin    = 1.dp
    val Base    = 1.5.dp
    val Heavy   = 2.dp
}

object TacticalElevation {
    val None    = 0.dp
    val Surface = 1.dp
    val Card    = 4.dp
    val Dialog  = 12.dp
    val Alert   = 24.dp
}

object TacticalType {
    // Labels (monospace / identifier text)
    val LabelTiny   = 8.sp
    val LabelSmall  = 10.sp
    val LabelBase   = 11.sp
    val LabelMedium = 12.sp

    // Body
    val BodySmall   = 13.sp
    val BodyBase    = 14.sp
    val BodyLarge   = 16.sp

    // Titles
    val TitleSmall  = 16.sp
    val TitleBase   = 18.sp
    val TitleLarge  = 22.sp

    // Display
    val Display     = 28.sp

    // Weights
    val WeightNormal    = FontWeight.Normal
    val WeightMedium    = FontWeight.Medium
    val WeightBold      = FontWeight.Bold
    val WeightBlack     = FontWeight.Black
    val WeightExtraBold = FontWeight.ExtraBold
}

/**
 * TacticalIcons — Standard icon sizes used across the system.
 * Prevents random icon size drift.
 */
object TacticalIcons {
    val Micro   = 12.dp
    val Tiny    = 14.dp
    val Small   = 16.dp
    val Base    = 20.dp
    val Medium  = 24.dp
    val Large   = 32.dp
    val Hero    = 48.dp
    val Display = 64.dp
}

/**
 * TacticalSizing — Common component height/width standards.
 */
object TacticalSizing {
    // Button heights
    val ButtonSmall  = 36.dp
    val ButtonBase   = 48.dp
    val ButtonLarge  = 56.dp

    // Input heights
    val InputBase    = 52.dp

    // Status indicators
    val StatusDot    = 8.dp
    val StatusDotLg  = 10.dp

    // Threat banner
    val ThreatBanner = 3.dp
    val AlertBanner  = 28.dp

    // Avatar
    val AvatarSmall  = 32.dp
    val AvatarBase   = 48.dp
    val AvatarLarge  = 80.dp
    val AvatarHero   = 96.dp
}

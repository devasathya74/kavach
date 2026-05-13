package com.kavach.app.ui.transitions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment

/**
 * TacticalTransitions — Procedural navigation transition presets.
 *
 * Design rules (per spec):
 *   ALLOWED   : hard fade, scan wipe, terminal reveal, tactical slide
 *   FORBIDDEN : spring, bounce, floating/elastic movement
 *
 * Usage in NavHost composable():
 *   composable(
 *       route           = Screen.Dashboard.route,
 *       enterTransition = { TacticalTransitions.enterFade },
 *       exitTransition  = { TacticalTransitions.exitFade }
 *   )
 */
object TacticalTransitions {

    private const val DURATION_FAST  = 200
    private const val DURATION_BASE  = 300
    private const val DURATION_SLOW  = 450

    // ── Hard Fade ─────────────────────────────────────────────
    // Clean, zero-movement alpha cut. Use for most screen transitions.

    val enterFade: EnterTransition = fadeIn(
        animationSpec = tween(DURATION_BASE, easing = LinearEasing)
    )

    val exitFade: ExitTransition = fadeOut(
        animationSpec = tween(DURATION_FAST, easing = LinearEasing)
    )

    // ── Tactical Slide (horizontal, no spring) ───────────────
    // Forward push: slide in from right. Back pop: slide out to right.

    val enterSlide: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth / 6 },
            animationSpec  = tween(DURATION_BASE, easing = LinearOutSlowInEasing)
        ) + fadeIn(tween(DURATION_FAST))

    val exitSlide: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -(fullWidth / 8) },
            animationSpec  = tween(DURATION_BASE, easing = FastOutLinearInEasing)
        ) + fadeOut(tween(DURATION_FAST))

    val popEnterSlide: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth / 6) },
            animationSpec  = tween(DURATION_BASE, easing = LinearOutSlowInEasing)
        ) + fadeIn(tween(DURATION_FAST))

    val popExitSlide: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth / 6 },
            animationSpec = tween(DURATION_BASE, easing = FastOutLinearInEasing)
        ) + fadeOut(tween(DURATION_FAST))

    // ── Terminal Reveal (scan from top) ──────────────────────
    // Used for critical screens: IntegrityScan, Splash, LockdownScreen.

    val enterTerminal: EnterTransition =
        slideInVertically(
            initialOffsetY = { -it / 10 },
            animationSpec  = tween(DURATION_SLOW, easing = LinearOutSlowInEasing)
        ) + fadeIn(tween(DURATION_BASE, easing = LinearEasing))

    val exitTerminal: ExitTransition =
        slideOutVertically(
            targetOffsetY = { it / 10 },
            animationSpec = tween(DURATION_BASE, easing = FastOutLinearInEasing)
        ) + fadeOut(tween(DURATION_FAST))

    // ── Scan Wipe (expand from center, procedural feel) ──────
    // For dashboard transitions between roles.

    val enterScan: EnterTransition =
        expandVertically(
            expandFrom    = Alignment.Top,
            animationSpec = tween(DURATION_BASE, easing = LinearOutSlowInEasing)
        ) + fadeIn(tween(DURATION_BASE))

    val exitScan: ExitTransition =
        shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(DURATION_FAST, easing = FastOutLinearInEasing)
        ) + fadeOut(tween(DURATION_FAST))

    // ── None (instant cut) ───────────────────────────────────
    // Use for emergency/lockdown screens where immediate appearance is critical.

    val enterImmediate: EnterTransition = EnterTransition.None
    val exitImmediate : ExitTransition  = ExitTransition.None
}

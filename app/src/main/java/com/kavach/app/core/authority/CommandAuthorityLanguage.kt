package com.kavach.app.core.authority

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kavach.app.core.sound.TacticalSound

/**
 * CommandAuthorityLanguage — Visual and behavioral distinction per authority source.
 *
 * Defines the visual treatment for commands, overlays, and indicators
 * based on WHO issued the action — not just what threat level it represents.
 *
 * Authority Sources:
 *   FIELD       — Normal user action (officer in field)
 *   PILOT       — Supervising pilot-level action
 *   COMMAND     — Commanding officer (Admin)
 *   SENANAYAK   — Highest command authority
 *   SYSTEM      — Automated system event (telemetry, integrity)
 *   EMERGENCY   — Crisis-level automated or manual event
 *
 * Each source produces a different:
 *   - Border width and color
 *   - Typography weight and size modifier
 *   - Header label text
 *   - Icon badge
 *   - Sound profile
 *   - Overlay geometry (tight vs expanded)
 */

enum class AuthoritySource {
    FIELD,
    PILOT,
    COMMAND,
    SENANAYAK,
    SYSTEM,
    EMERGENCY
}

data class AuthorityVisualSpec(
    val source           : AuthoritySource,
    val label            : String,          // Display label
    val borderColor      : Color,
    val borderWidth      : Dp,
    val accentColor      : Color,
    val headerWeight     : FontWeight,
    val badgeColorHex    : Long,
    val sound            : TacticalSound?,
    val overlayFullScreen: Boolean,         // True for SENANAYAK/EMERGENCY
    val prefixSymbol     : String           // Shown before title in overlays
)

object CommandAuthorityLanguage {

    val FIELD = AuthorityVisualSpec(
        source            = AuthoritySource.FIELD,
        label             = "FIELD",
        borderColor       = Color(0xFF334155),
        borderWidth       = 1.dp,
        accentColor       = Color(0xFF94A3B8),
        headerWeight      = FontWeight.Medium,
        badgeColorHex     = 0xFF334155,
        sound             = null,
        overlayFullScreen = false,
        prefixSymbol      = "○"
    )

    val PILOT = AuthorityVisualSpec(
        source            = AuthoritySource.PILOT,
        label             = "PILOT COMMAND",
        borderColor       = Color(0xFF1D4ED8),
        borderWidth       = 1.5.dp,
        accentColor       = Color(0xFF60A5FA),
        headerWeight      = FontWeight.Bold,
        badgeColorHex     = 0xFF1D4ED8,
        sound             = TacticalSound.ALERT_PING,
        overlayFullScreen = false,
        prefixSymbol      = "◆"
    )

    val COMMAND = AuthorityVisualSpec(
        source            = AuthoritySource.COMMAND,
        label             = "COMMANDING OFFICER",
        borderColor       = Color(0xFFB45309),
        borderWidth       = 2.dp,
        accentColor       = Color(0xFFF59E0B),
        headerWeight      = FontWeight.ExtraBold,
        badgeColorHex     = 0xFFB45309,
        sound             = TacticalSound.ESCALATION_TONE,
        overlayFullScreen = false,
        prefixSymbol      = "▲"
    )

    val SENANAYAK = AuthorityVisualSpec(
        source            = AuthoritySource.SENANAYAK,
        label             = "SENANAYAK DIRECTIVE",
        borderColor       = Color(0xFFDC2626),
        borderWidth       = 2.5.dp,
        accentColor       = Color(0xFFEF4444),
        headerWeight      = FontWeight.Black,
        badgeColorHex     = 0xFFDC2626,
        sound             = TacticalSound.THREAT_WARNING,
        overlayFullScreen = true,
        prefixSymbol      = "⊗"
    )

    val SYSTEM = AuthorityVisualSpec(
        source            = AuthoritySource.SYSTEM,
        label             = "SYSTEM EVENT",
        borderColor       = Color(0xFF0F766E),
        borderWidth       = 1.dp,
        accentColor       = Color(0xFF2DD4BF),
        headerWeight      = FontWeight.Medium,
        badgeColorHex     = 0xFF0F766E,
        sound             = null,
        overlayFullScreen = false,
        prefixSymbol      = "■"
    )

    val EMERGENCY = AuthorityVisualSpec(
        source            = AuthoritySource.EMERGENCY,
        label             = "EMERGENCY AUTHORITY",
        borderColor       = Color(0xFF7F1D1D),
        borderWidth       = 3.dp,
        accentColor       = Color(0xFFDC2626),
        headerWeight      = FontWeight.Black,
        badgeColorHex     = 0xFF7F1D1D,
        sound             = TacticalSound.EMERGENCY_BROADCAST,
        overlayFullScreen = true,
        prefixSymbol      = "✕"
    )

    /** Resolve from a role string to the appropriate authority spec. */
    fun fromRole(role: String): AuthorityVisualSpec = when (role.uppercase()) {
        "SUPERUSER", "SENANAYAK"              -> SENANAYAK
        "COMMANDING_OFFICER", "ADMIN"         -> COMMAND
        "PILOT"                               -> PILOT
        "SYSTEM", "TELEMETRY", "INTEGRITY"    -> SYSTEM
        "EMERGENCY"                           -> EMERGENCY
        else                                  -> FIELD
    }

    /** All specs for UI rendering in the authority legend. */
    val all = listOf(FIELD, PILOT, COMMAND, SENANAYAK, SYSTEM, EMERGENCY)
}

package com.kavach.app.ui.theme

import androidx.compose.ui.graphics.Color

object TacticalColors {
    // Core tactical palette – dark theme, high contrast, battery‑friendly
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val Primary = Color(0xFFB71C1C) // SOS / emergency red
    val OnPrimary = Color(0xFFFFFFFF)
    val Connected = Color(0xFF00FF00) // bright green for good connection
    val Connecting = Color(0xFFCCCC00) // amber
    val Reconnecting = Color(0xFFFFA500) // orange
    val Offline = Color(0xFFFF0000) // red
    val Failed = Color(0xFF800000) // dark red
    val WarningStrip = Color(0x80FF0000) // semi‑transparent red for degraded overlay
    // Additional colors for different modes can be added here
}

package com.kavach.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme Colors
val LightBackground = Color(0xFFF5F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimary = Color(0xFF1E3A5F)
val LightSecondary = Color(0xFF3B82F6)
val LightTextPrimary = Color(0xFF111827)
val LightTextSecondary = Color(0xFF6B7280)

// Dark Theme Colors
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF111827)
val DarkPrimary = Color(0xFF60A5FA)
val DarkSecondary = Color(0xFF93C5FD)
val DarkTextPrimary = Color(0xFFF9FAFB)
val DarkTextSecondary = Color(0xFFCBD5E1)

// Shared Semantic Colors
val ColorSuccess = Color(0xFF16A34A)
val ColorWarning = Color(0xFFF59E0B)
val ColorError = Color(0xFFDC2626)

// Old theme fallbacks (Temporary for compilation safety)
val NavyBlueDark = DarkBackground
val NavyBlue = DarkSurface
val GoldenYellow = ColorWarning
val Surface1 = DarkSurface
val Surface2 = Color(0xFF1E293B)
val Surface3 = Color(0xFF334155)
val OnSurface = DarkTextPrimary
val OnSurfaceMid = DarkTextSecondary
val OnSurfaceLow = Color(0xFF94A3B8)
val Divider = Color(0xFF334155)
val LightRed = ColorError
val LightGreen = ColorSuccess
val DangerRed = ColorError
val SuccessGreen = ColorSuccess
val WarningOrange = ColorWarning
val NavyBlueLight = DarkPrimary
val CyberGreen = Color(0xFF4ADE80)
val NavyBlueDarker = Color(0xFF020617)

// Missing legacy fallbacks
val OfficialBackground = LightBackground
val SurfaceWhite = LightSurface
val TextPrimary = LightTextPrimary
val TextSecondary = LightTextSecondary

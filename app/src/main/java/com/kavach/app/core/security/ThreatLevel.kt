package com.kavach.app.core.security

/**
 * ThreatLevel — Centralized operational security states.
 * Used globally across the app to gate features, trigger overlays,
 * and modulate visual telemetry indicators.
 */
enum class ThreatLevel(
    val code        : Int,
    val label       : String,
    val colorHex    : Long,
    val pulseBpm    : Int,      // visual pulse rate
    val blocksNavigation : Boolean,
    val requiresAck : Boolean
) {
    SECURE      (0, "SECURE",      0xFF16A34A, 60,  false, false),
    WARNING     (1, "WARNING",     0xFFF59E0B, 90,  false, false),
    ELEVATED    (2, "ELEVATED",    0xFFEA580C, 120, false, true),
    CRITICAL    (3, "CRITICAL",    0xFFDC2626, 180, true,  true),
    COMPROMISED (4, "COMPROMISED", 0xFF7F1D1D, 240, true,  true);

    val isBlocking get() = blocksNavigation
    val isCritical get() = this >= CRITICAL
}

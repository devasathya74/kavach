package com.kavach.app.utils

/**
 * Operational Lexicon - Simplification for Stress Conditions.
 * Maps internal technical engine states to field-usable wording.
 */
object OperationalStrings {
    
    /**
     * Maps engine states to Pilot/User UI labels.
     */
    fun mapStateToDisplay(internalState: String): String {
        return when (internalState.uppercase()) {
            // Internal Engine -> Field Wording
            "PARTITION_DETECTED", "PARTITIONED" -> "Network Unstable"
            "REPLAY_LAG", "SYNCING"            -> "Sync Delayed"
            "DEGRADED_MODE", "DEGRADED"        -> "Limited Connectivity"
            "TRUST_RECALIBRATION", "VERIFYING" -> "Verification Pending"
            "LOCKDOWN"                         -> "Security Restriction"
            "HEALING"                          -> "Restoring Sync"
            "NORMAL", "HEALTHY"                -> "System Operational"
            
            else -> internalState.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Maps internal severity to color tokens or user wording.
     */
    fun mapSeverity(severity: String): String {
        return when (severity.uppercase()) {
            "CRITICAL", "SECURITY" -> "High Priority"
            "WARNING"             -> "Attention Required"
            "INFO"                -> "Standard"
            else                  -> severity
        }
    }
}

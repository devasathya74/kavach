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

    /**
     * Map HTTP codes to clear field actions.
     */
    fun mapHttpError(code: Int): String {
        return when (code) {
            401 -> "सत्र समाप्त (Session Expired)"
            403 -> "डिवाइस मेल नहीं खाता (Device Mismatch)"
            404 -> "अज्ञात अनुरोध (Resource Not Found)"
            503 -> "🛠️ सर्वर में रखरखाव (Maintenance) चल रहा है"
            in 500..599 -> "सर्वर त्रुटि (Server Under Load)"
            else -> "नेटवर्क त्रुटि (Network Error: $code)"
        }
    }
}

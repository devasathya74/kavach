package com.kavach.app.ui.navigation

/**
 * RoleRouter — Single source of truth for role string normalization.
 *
 * The backend (Django) may return role strings in various formats.
 * This object normalizes ALL known backend formats → canonical app routes.
 *
 * CANONICAL ROLE STRINGS (used everywhere in NavHost):
 *   "SENANAYAK"   → Command Center (admin, highest authority)
 *   "PILOT"       → Pilot Dashboard (supervisor/pilot officer)
 *   "USER"        → Field Dashboard (normal field officer)
 *
 * LOCAL TEST BYPASS (for offline/pilot testing without backend):
 *   PNO "000000000" → SENANAYAK
 *   PNO "111111111" → PILOT
 *   PNO "222222222" → USER
 */
object RoleRouter {

    // All known backend role string variants → canonical
    private val ROLE_MAP = mapOf(
        "ADMIN"               to "ADMIN",
        "COMMANDING_OFFICER"  to "ADMIN",
        "SENANAYAK"           to "ADMIN",
        "PILOT"               to "PILOT",
        "USER"                to "USER",
    )

    /**
     * Normalize any backend role string → canonical role.
     * Case-insensitive. Falls back to "USER" if unknown.
     */
    fun normalize(rawRole: String): String =
        ROLE_MAP[rawRole.trim().uppercase()] ?: ROLE_MAP[rawRole.trim()] ?: "USER"

    /**
     * Resolve dashboard Screen route from canonical role.
     * MUST return namespaced routes only (admin/dashboard, pilot/dashboard, user/dashboard).
     */
    fun dashboardRoute(canonicalRole: String): String = when (normalize(canonicalRole)) {
        "ADMIN" -> Screen.AdminDashboard.route
        "PILOT" -> Screen.PilotDashboard.route
        else    -> Screen.UserDashboard.route
    }

    /**
     * LOCAL TEST BYPASS — for pilot testing without a live backend.
     * Resolve canonical role from PNO (overrides any stored role).
     *
     * DISABLE THIS in production: set ENABLED = false.
     */
    object LocalTestBypass {
        const val ENABLED = true   // ← Set false before production release

        private val PNO_ROLE_MAP = mapOf(
            "000000000" to "ADMIN",
            "111111111" to "PILOT",
            "222222222" to "USER",
        )

        fun resolveRole(pno: String): String? =
            if (ENABLED) PNO_ROLE_MAP[pno.trim()] else null
    }
}

package com.kavach.app.ui.navigation

sealed class Screen(val route: String) {
    // Boot
    object Splash          : Screen("splash")


    // Onboarding
    object ConnectivityTest : Screen("connectivity_test")
    object Consent          : Screen("consent")
    object PermissionGate   : Screen("permission_gate")

    // Auth
    object Login    : Screen("login")
    object SecurityEnrollment : Screen("security_enrollment")
    object SecureAccess       : Screen("secure_access")
    // object OtpVerify: Screen("otp_verify/{pno}") {
    //     fun createRoute(pno: String) = "otp_verify/$pno"
    // }

    // ── LEGACY ROUTES (Stability Aliases) ──────────────────────
    object Dashboard      : Screen("dashboard")        // Legacy Entry Point
    object OldPilotDashboard : Screen("pilot_dashboard") // To be purged
    object OldAdminDashboard : Screen("admin_dashboard") // To be purged

    // ── ROLE DASHBOARDS (New Namespaces) ────────────────────────
    object AdminDashboard : Screen("admin/dashboard")
    object PilotDashboard : Screen("pilot/dashboard")
    object UserDashboard  : Screen("user/dashboard")

    object PilotDiagnostics : Screen("pilot_diagnostics")
    object Restricted       : Screen("restricted")
    object LiveBroadcast    : Screen("live_broadcast")

    // ── Training ─────────────────────────────────────────────────
    object TrainingList : Screen("training_list")
    object VideoPlayer  : Screen("video_player/{trainingId}") {
        fun createRoute(id: String) = "video_player/$id"
    }
    object Quiz         : Screen("quiz/{trainingId}") {
        fun createRoute(id: String) = "quiz/$id"
    }
    object QuizResult   : Screen("quiz_result/{trainingId}/{score}") {
        fun createRoute(id: String, score: Int) = "quiz_result/$id/$score"
    }

    // ── Orders ───────────────────────────────────────────────────
    object OrderList   : Screen("order_list")
    object OrderDetail : Screen("order_detail/{orderId}") {
        fun createRoute(id: String) = "order_detail/$id"
    }

    // ── Profile ──────────────────────────────────────────────────
    object Profile             : Screen("profile")
    object DeviceChangeRequest : Screen("device_change_request")

    // ── PILOT MODULES (Namespaced) ───────────────────────────────
    object PilotPersonnel  : Screen("pilot/personnel")
    object PilotIncidents  : Screen("pilot/incidents")
    object PilotDevices    : Screen("pilot/devices")
    object PilotApprovals  : Screen("pilot/approvals")
    object PilotBroadcast  : Screen("pilot/broadcast")
    object PilotTraining   : Screen("pilot/training")
    object PilotOta        : Screen("pilot/ota")
    object PilotAudit      : Screen("pilot/audit")
    object PilotFieldData  : Screen("pilot/field_data")

    // ── Legacy Operational Routes (Flat) ─────────────────────────
    object UserManagement  : Screen("route_users")
    object UserDetail      : Screen("route_users/{userId}") {
        fun createRoute(userId: String) = "route_users/$userId"
    }
    object UserRegistration : Screen("route_register_user?userId={userId}") {
        fun createRoute(userId: String? = null) = if (userId != null) "route_register_user?userId=$userId" else "route_register_user"
    }
    object PendingApprovals : Screen("route_approvals")
    object OtaUpdate        : Screen("route_ota")
    object IncidentCenter   : Screen("route_incidents")
    object DeviceMonitor    : Screen("route_devices")
    object BroadcastInbox   : Screen("route_broadcast_inbox")
    object BroadcastCenter  : Screen("route_broadcast_center")
    object CreateBroadcast  : Screen("route_create_broadcast")
    object FieldData        : Screen("route_field_data")
    object AuditCenter      : Screen("route_audit")
// ----- New UI Routes -----
object Home       : Screen("home")
object Alerts     : Screen("alerts")
object Broadcast  : Screen("broadcast")
object Units      : Screen("units")
object Profile    : Screen("profile")
object TrainingScreen : Screen("training_screen")
    // ── ComingSoon placeholder routes ─────────────────────────────
    // Each module that is architecturally complete but not yet
    // backend-integrated gets its own route so navigation works.
    object ComingSoon       : Screen("coming_soon/{title}") {
        fun createRoute(title: String) = "coming_soon/$title"
    }
}

package com.kavach.app.ui.navigation

sealed class Screen(val route: String) {
    // Onboarding
    object ConnectivityTest : Screen("connectivity_test")
    object Consent  : Screen("consent")
    object PermissionGate : Screen("permission_gate")

    // Auth
    object Login    : Screen("login")
    object OtpVerify: Screen("otp_verify/{pno}") {
        fun createRoute(pno: String) = "otp_verify/$pno"
    }

    // Main
    object Dashboard : Screen("dashboard")
    object AdminDashboard : Screen("admin_dashboard")
    object PilotDashboard : Screen("pilot_dashboard")
    object PilotDiagnostics : Screen("pilot_diagnostics")
    object Restricted     : Screen("restricted")
    object LiveBroadcast  : Screen("live_broadcast")

    // Training
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

    // Orders
    object OrderList          : Screen("order_list")
    object OrderDetail        : Screen("order_detail/{orderId}") {
        fun createRoute(id: String) = "order_detail/$id"
    }

    // Profile & Settings
    object Profile              : Screen("profile")
    object DeviceChangeRequest  : Screen("device_change_request")

    // Pilot Dashboard Modules
    object UserManagement     : Screen("route_users")
    object UserDetail         : Screen("route_users/{userId}") {
        fun createRoute(userId: String) = "route_users/$userId"
    }
    object PendingApprovals   : Screen("route_approvals")
    object OtaUpdate          : Screen("route_ota")
    object IncidentCenter     : Screen("route_incidents")
    object DeviceMonitor      : Screen("route_devices")
    object BroadcastInbox     : Screen("route_broadcast_inbox")
    object BroadcastCenter    : Screen("route_broadcast_center")
    object CreateBroadcast    : Screen("route_create_broadcast")
    object FieldData          : Screen("route_field_data")
    object AuditCenter        : Screen("route_audit")
}

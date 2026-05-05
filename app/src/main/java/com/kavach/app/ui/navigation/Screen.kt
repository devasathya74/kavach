package com.kavach.app.ui.navigation

sealed class Screen(val route: String) {
    // Onboarding
    object Consent  : Screen("consent")

    // Auth
    object Login    : Screen("login")
    object OtpVerify: Screen("otp_verify/{pno}") {
        fun createRoute(pno: String) = "otp_verify/$pno"
    }

    // Main
    object Dashboard : Screen("dashboard")
    object AdminDashboard : Screen("admin_dashboard")
    object LiveBroadcast : Screen("live_broadcast")

    // Training
    object TrainingList : Screen("training_list")
    object VideoPlayer  : Screen("video_player/{trainingId}") {
        fun createRoute(id: Int) = "video_player/$id"
    }
    object Quiz         : Screen("quiz/{trainingId}") {
        fun createRoute(id: Int) = "quiz/$id"
    }
    object QuizResult   : Screen("quiz_result/{trainingId}/{score}") {
        fun createRoute(id: Int, score: Int) = "quiz_result/$id/$score"
    }

    // Orders
    object OrderList          : Screen("order_list")
    object OrderDetail        : Screen("order_detail/{orderId}") {
        fun createRoute(id: String) = "order_detail/$id"
    }

    // Profile & Settings
    object Profile              : Screen("profile")
    object DeviceChangeRequest  : Screen("device_change_request")
}

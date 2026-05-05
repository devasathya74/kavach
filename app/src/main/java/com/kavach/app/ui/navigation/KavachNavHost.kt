package com.kavach.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.ui.screens.dashboard.DashboardScreen
import com.kavach.app.ui.screens.login.ConsentScreen
import com.kavach.app.ui.screens.login.LoginScreen
import com.kavach.app.ui.screens.login.OtpVerifyScreen
import com.kavach.app.ui.screens.orders.OrderDetailScreen
import com.kavach.app.ui.screens.orders.OrderListScreen
import com.kavach.app.ui.screens.profile.ProfileScreen
import com.kavach.app.ui.screens.training.QuizResultScreen
import com.kavach.app.ui.screens.training.QuizScreen
import com.kavach.app.ui.screens.training.TrainingListScreen
import com.kavach.app.ui.screens.training.VideoPlayerScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Root navigation host.
 *
 * Start destination logic:
 *  ① Consent not accepted → Consent screen
 *  ② Token missing/expired → Login
 *  ③ Valid session → Dashboard
 *
 * Session timeout:
 *  onUserInteraction lambda passed from MainActivity → calls SessionTimeoutManager.ping()
 *  Wrap entire NavHost in pointerInput to capture any touch → ping timer
 */
@Composable
fun KavachNavHost(
    navController      : NavHostController = rememberNavController(),
    sessionDataStore   : SessionDataStore  = hiltViewModel<NavHostViewModel>().sessionDataStore,
    onUserInteraction  : () -> Unit        = {}
) {
    val scope              = rememberCoroutineScope()
    val consentAccepted    by sessionDataStore.consentAccepted.collectAsState(initial = false)
    val token              by sessionDataStore.token.collectAsState(initial = null)

    val isStaff            by sessionDataStore.isStaff.collectAsState(initial = false)

    // Determine start destination
    val startDestination = when {
        !consentAccepted -> Screen.Consent.route
        token.isNullOrBlank() -> Screen.Login.route
        isStaff -> Screen.AdminDashboard.route
        else -> Screen.Dashboard.route
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier = Modifier.pointerInput(Unit) {
            // Any touch anywhere → ping session timeout timer
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent()
                    onUserInteraction()
                }
            }
        }
    ) {

        /* ── Consent (first launch) ── */
        composable(Screen.Consent.route) {
            ConsentScreen(
                onAccepted = {
                    scope.launch {
                        sessionDataStore.saveConsent()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Consent.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        /* ── Auth ── */
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { pno ->
                    navController.navigate(Screen.OtpVerify.createRoute(pno)) {
                        popUpTo(Screen.Login.route) { inclusive = false }
                    }
                },
                onAdminLoggedIn = {
                    navController.navigate(Screen.AdminDashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.OtpVerify.route,
            arguments = listOf(navArgument("pno") { type = NavType.StringType })
        ) {
            OtpVerifyScreen(
                onVerified = {
                    val dest = if (isStaff) Screen.AdminDashboard.route else Screen.Dashboard.route
                    navController.navigate(dest) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        /* ── Dashboard ── */
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onGoToLive     = { navController.navigate(Screen.LiveBroadcast.route) },
                onGoToOrders   = { navController.navigate(Screen.OrderList.route) },
                onGoToProfile  = { navController.navigate(Screen.Profile.route) }
            )
        }

        /* ── Admin Dashboard ── */
        composable(Screen.AdminDashboard.route) {
            com.kavach.app.ui.screens.admin.AdminDashboardScreen(
                onLogout = {
                    scope.launch {
                        sessionDataStore.clearSession()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        /* ── Live Broadcast ── */
        composable(Screen.LiveBroadcast.route) {
            com.kavach.app.ui.screens.live.LiveBroadcastScreen(
                onBack = { navController.popBackStack() }
            )
        }

        /* ── Training ── */
        composable(Screen.TrainingList.route) {
            TrainingListScreen(
                onTrainingClick = { id -> navController.navigate(Screen.VideoPlayer.createRoute(id)) },
                onBack          = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(navArgument("trainingId") { type = NavType.IntType })
        ) { backStackEntry ->
            val trainingId = backStackEntry.arguments?.getInt("trainingId") ?: return@composable
            VideoPlayerScreen(
                trainingId      = trainingId,
                onVideoComplete = { navController.navigate(Screen.Quiz.createRoute(trainingId)) }
            )
        }

        composable(
            route = Screen.Quiz.route,
            arguments = listOf(navArgument("trainingId") { type = NavType.IntType })
        ) { backStackEntry ->
            val trainingId = backStackEntry.arguments?.getInt("trainingId") ?: return@composable
            QuizScreen(
                trainingId = trainingId,
                onSubmit   = { score ->
                    navController.navigate(Screen.QuizResult.createRoute(trainingId, score)) {
                        popUpTo(Screen.TrainingList.route)
                    }
                }
            )
        }

        composable(
            route = Screen.QuizResult.route,
            arguments = listOf(
                navArgument("trainingId") { type = NavType.IntType },
                navArgument("score")      { type = NavType.IntType }
            )
        ) {
            QuizResultScreen(
                onContinue = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        /* ── Orders ── */
        composable(Screen.OrderList.route) {
            OrderListScreen(
                onOrderClick = { id -> navController.navigate(Screen.OrderDetail.createRoute(id)) },
                onBack       = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.OrderDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) {
            OrderDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        /* ── Profile ── */
        composable(Screen.Profile.route) {
            ProfileScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

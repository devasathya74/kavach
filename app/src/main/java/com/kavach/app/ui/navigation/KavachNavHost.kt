package com.kavach.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.ui.screens.dashboard.DashboardScreen
import com.kavach.app.ui.screens.dashboard.RestrictedDashboardScreen
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
    viewModel          : NavHostViewModel  = hiltViewModel(),
    onUserInteraction  : () -> Unit        = {}
) {
    val sessionDataStore   = viewModel.sessionDataStore
    val scope              = rememberCoroutineScope()
    val lifecycleOwner     = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    val consentAccepted    by sessionDataStore.consentAccepted.collectAsState(initial = false)
    val role               by sessionDataStore.role.collectAsState(initial = "")
    val token              by sessionDataStore.token.collectAsState(initial = null)

    val isVerifiedInThisSession by viewModel.isVerifiedInThisSession.collectAsState()
    val isLimitedMode           by viewModel.isLimitedMode.collectAsState()
    val isRecovering            by viewModel.isRecovering.collectAsState()
    val recoveryError           by viewModel.error.collectAsState()

    // Lifecycle Authority Guard: Sync on Every Resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (!token.isNullOrBlank()) {
                    scope.launch { viewModel.syncProfile() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }



    // Reactive Navigation Logic: 
    // Redirect whenever a verified Role is detected
    LaunchedEffect(role, isVerifiedInThisSession, isLimitedMode) {
        val currentEntry = navController.currentBackStackEntry
        if (currentEntry != null) {
            
            // SECURITY RULE: Never trust yesterday's truth.
            // If not verified in this session AND NOT in limited mode, we wait.
            // If in Limited Mode, we force the lowest privilege (Standard Dashboard).
            val dest = if (isVerifiedInThisSession) {
                when (role) {
                    "ADMIN", "SUPERUSER" -> Screen.AdminDashboard.route
                    "PILOT" -> Screen.PilotDashboard.route
                    else -> Screen.Dashboard.route
                }
            } else if (isLimitedMode) {
                Screen.Restricted.route
            } else {
                return@LaunchedEffect // Wait for verification or manual Limited Mode choice
            }
            
            val currentRoute = currentEntry.destination.route
            val isAtAuthScreen = currentRoute == null || 
                                currentRoute == Screen.Login.route || 
                                currentRoute == Screen.OtpVerify.route || 
                                currentRoute == Screen.Consent.route

            if (isAtAuthScreen && currentRoute != dest) {
                navController.navigate(dest) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Adaptive Resilience: Exponential Backoff Retry logic with Jitter
    // Capped to 4 attempts (approx 15-20s total) to preserve battery/backend load
    LaunchedEffect(token, role, isVerifiedInThisSession) {
        if (!token.isNullOrBlank() && !isVerifiedInThisSession) {
            val baseDelays = listOf(1000L, 2000L, 4000L, 8000L)
            
            for (baseDelay in baseDelays) {
                if (isVerifiedInThisSession) break
                
                val jitter = (0..500).random().toLong()
                kotlinx.coroutines.delay(baseDelay + jitter)
                
                if (!isVerifiedInThisSession) {
                    viewModel.syncProfile()
                }
            }
        }
    }

    // Determine start destination (Initial App Boot)
    val startDestination = if (!consentAccepted) Screen.Consent.route
                          else if (token.isNullOrBlank()) Screen.Login.route
                          else Screen.Dashboard.route 

    Box(modifier = Modifier.fillMaxSize()) {
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
            // ... (rest of NavHost content)
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
                        navController.navigate(Screen.OtpVerify.createRoute(pno))
                    },
                    onAdminLoggedIn = { /* LaunchedEffect will handle redirection */ }
                )
            }

            composable(
                route = Screen.OtpVerify.route,
                arguments = listOf(navArgument("pno") { type = NavType.StringType })
            ) {
                OtpVerifyScreen(
                    onVerified = { /* LaunchedEffect will handle redirection */ }
                )
            }

            /* ── Restricted (Limited Mode) ── */
            composable(Screen.Restricted.route) {
                RestrictedDashboardScreen(
                    onRetry  = { viewModel.manualRetry() },
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
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
// ... rest of routes ...

            /* ── Admin Dashboard ── */
            composable(Screen.AdminDashboard.route) {
                com.kavach.app.ui.screens.admin.AdminDashboardScreen(
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            /* ── Pilot Dashboard ── */
            composable(Screen.PilotDashboard.route) {
                com.kavach.app.ui.screens.pilot.PilotDashboardScreen(
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
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

            /* ── Orders ── */
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
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Loading Overlay for pending Role resolution
        if (!token.isNullOrBlank() && role.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.kavach.app.ui.theme.NavyBlueDark.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = com.kavach.app.ui.theme.GoldenYellow
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Text(
                        text  = if (recoveryError != null) recoveryError!! else "सुरक्षित सत्र सत्यापित किया जा रहा है...",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = if (recoveryError != null) androidx.compose.material3.MaterialTheme.colorScheme.error else com.kavach.app.ui.theme.OnSurface
                    )

                    Spacer(Modifier.height(24.dp))

                    Row {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.manualRetry() },
                            enabled = !isRecovering
                        ) {
                            androidx.compose.material3.Text(
                                text  = if (isRecovering) "प्रतीक्षा करें..." else "पुनः प्रयास करें",
                                color = com.kavach.app.ui.theme.GoldenYellow
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Mission Continuity: Allow standard dashboard access if verification is slow
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.enterLimitedMode() }
                        ) {
                            androidx.compose.material3.Text("सीमित मोड", color = com.kavach.app.ui.theme.OnSurface.copy(alpha = 0.7f))
                        }

                        Spacer(Modifier.width(8.dp))

                        androidx.compose.material3.TextButton(onClick = { viewModel.logout() }) {
                            androidx.compose.material3.Text("लॉगआउट", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Persistent "Limited Mode" Banner
        if (isLimitedMode && !token.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(com.kavach.app.ui.theme.GoldenYellow.copy(alpha = 0.9f))
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text  = "⚠️ सीमित मोड सक्रिय - डेटा सत्यापित नहीं है",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = com.kavach.app.ui.theme.NavyBlueDark
                )
            }
        }
    }
}


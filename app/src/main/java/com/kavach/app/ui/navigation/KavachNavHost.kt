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
import com.kavach.app.ui.screens.dashboard.UnifiedDashboardScreen
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
import com.kavach.app.ui.screens.pilot.personnel.PersonnelListScreen
import com.kavach.app.ui.screens.pilot.personnel.OfficerDetailScreen
import com.kavach.app.ui.screens.pilot.approvals.ApprovalListScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.kavach.app.util.ConnectionStatus
import com.kavach.app.ui.components.ConnectivityBanner


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
    val permissionsHandled by sessionDataStore.permissionsHandled.collectAsState(initial = false)
    val role               by sessionDataStore.role.collectAsState(initial = null)
    val token              by sessionDataStore.token.collectAsState(initial = null)
    val deviceSecret       by sessionDataStore.deviceSecret.collectAsState(initial = null)

    var sessionReady by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(token, role) {
        sessionReady = true
    }

    val isVerifiedInThisSession by viewModel.isVerifiedInThisSession.collectAsState(initial = false)
    val isLimitedMode           by viewModel.isLimitedMode.collectAsState()
    val isRecovering            by viewModel.isRecovering.collectAsState()
    val recoveryError           by viewModel.error.collectAsState()
    val connectionStatus        by viewModel.connectionStatus.collectAsState(initial = ConnectionStatus.AVAILABLE)



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

    // Deterministic Startup Logic: Wait for DataStore to emit non-null values
    // This prevents flickering and accidental login redirection during initial load
    val isStartupLoaded = token != null && role != null && consentAccepted != null && permissionsHandled != null && deviceSecret != null

    if (!isStartupLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = com.kavach.app.ui.theme.GoldenYellow)
        }
        return
    }

    val startDestination = remember(token, role, consentAccepted, permissionsHandled, deviceSecret) {
        when {
            consentAccepted == false -> Screen.Consent.route
            permissionsHandled == false -> Screen.PermissionGate.route
            token.isNullOrBlank() || deviceSecret.isNullOrBlank() -> Screen.Login.route
            role == "COMMANDING_OFFICER" -> Screen.AdminDashboard.route
            role == "PILOT" -> Screen.PilotDashboard.route
            role == "USER" -> Screen.Dashboard.route
            else -> Screen.Login.route
        }
    }

    LaunchedEffect(token, role, consentAccepted, permissionsHandled) {
        android.util.Log.d("KAVACH_NAV", "STARTUP_DET: TOKEN=${token?.take(4)}... ROLE=$role CONSENT=$consentAccepted PERMS=$permissionsHandled")
    }
    
    // ── Global Session Monitor ─────────────────────────────
    // If token is cleared (e.g. by Authenticator on refresh failure), 
    // force immediate redirect to Login.
    LaunchedEffect(token) {
        if (sessionReady && token.isNullOrBlank()) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }



    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ConnectivityBanner(status = connectionStatus)
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
                
                /* ── Connectivity Diagnostic (Pilot Fast Fail) ── */
            composable(Screen.ConnectivityTest.route) {
                com.kavach.app.ui.screens.pilot.ConnectivityScreen(
                    onSuccess = {
                        val dest = if (!consentAccepted) {
                            Screen.Consent.route
                        } else if (token.isNullOrBlank()) {
                            Screen.Login.route
                        } else {
                            when (role) {
                                "COMMANDING_OFFICER" -> Screen.AdminDashboard.route
                                "PILOT" -> Screen.PilotDashboard.route
                                "USER" -> Screen.Dashboard.route
                                else -> Screen.Login.route
                            }
                        }
                        navController.navigate(dest) {
                            popUpTo(Screen.ConnectivityTest.route) { inclusive = true }
                        }
                    }
                )
            }
            // ... (rest of NavHost content)
            /* ── Consent (first launch) ── */
            composable(Screen.Consent.route) {
                ConsentScreen(
                    onAccepted = {
                        scope.launch {
                            sessionDataStore.saveConsent()
                            navController.navigate(Screen.PermissionGate.route) {
                                popUpTo(Screen.Consent.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            /* ── Mission Readiness Gate (Permissions) ── */
            composable(Screen.PermissionGate.route) {
                com.kavach.app.ui.screens.permissions.PermissionGateScreen(
                    onContinue = {
                        val dest = if (token.isNullOrBlank()) Screen.Login.route else {
                            when (role) {
                                "COMMANDING_OFFICER" -> Screen.AdminDashboard.route
                                "PILOT" -> Screen.PilotDashboard.route
                                "USER" -> Screen.Dashboard.route
                                else -> Screen.Login.route
                            }
                        }
                        navController.navigate(dest) {
                            popUpTo(Screen.PermissionGate.route) { inclusive = true }
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
                    onAdminLoggedIn = {
                        scope.launch {
                            val currentRole = sessionDataStore.role.first()
                            val dest = when (currentRole) {
                                "COMMANDING_OFFICER" -> Screen.AdminDashboard.route
                                "PILOT" -> Screen.PilotDashboard.route
                                "USER" -> Screen.Dashboard.route
                                else -> Screen.Login.route
                            }
                            navController.navigate(dest) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    },
                    onDiagnosticsClick = {
                        navController.navigate(Screen.PilotDiagnostics.route)
                    }
                )
            }

            composable(Screen.PilotDiagnostics.route) {
                com.kavach.app.ui.screens.pilot.PilotDiagnosticsScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.OtpVerify.route,
                arguments = listOf(navArgument("pno") { type = NavType.StringType })
            ) {
                OtpVerifyScreen(
                    onVerified = {
                        scope.launch {
                            val currentRole = sessionDataStore.role.first()
                            val dest = when (currentRole) {
                                "COMMANDING_OFFICER" -> Screen.AdminDashboard.route
                                "PILOT" -> Screen.PilotDashboard.route
                                "USER" -> Screen.Dashboard.route
                                else -> Screen.Login.route
                            }
                            navController.navigate(dest) {
                                popUpTo(Screen.OtpVerify.route) { inclusive = true }
                            }
                        }
                    }
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

            /* ── Unified Dashboard (Role-Based) ── */
            composable(Screen.Dashboard.route) {
                UnifiedDashboardScreen(
                    role = role ?: "USER",
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            
            composable(Screen.AdminDashboard.route) {
                UnifiedDashboardScreen(
                    role = "COMMANDING_OFFICER",
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            composable(Screen.PilotDashboard.route) {
                UnifiedDashboardScreen(
                    role = "PILOT",
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
// ... rest of routes ...



            composable(Screen.UserManagement.route) {
                PersonnelListScreen(
                    onUserClick = { userId -> 
                        navController.navigate(Screen.UserDetail.createRoute(userId))
                    }
                )
            }

            composable(
                route = Screen.UserDetail.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                OfficerDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.PendingApprovals.route) {
                ApprovalListScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.IncidentCenter.route) {
                com.kavach.app.ui.screens.pilot.incident.IncidentCenterScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.BroadcastInbox.route) {
                com.kavach.app.ui.screens.broadcast.BroadcastInboxScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.BroadcastCenter.route) {
                com.kavach.app.ui.screens.pilot.broadcast.BroadcastCenterScreen(
                    onBack = { navController.popBackStack() },
                    onCreateBroadcast = { navController.navigate(Screen.CreateBroadcast.route) }
                )
            }
            composable(Screen.CreateBroadcast.route) {
                com.kavach.app.ui.screens.broadcast.CreateBroadcastScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DeviceMonitor.route) {
                com.kavach.app.ui.screens.device.DeviceStatusScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.OtaUpdate.route) { 
                com.kavach.app.ui.screens.ota.OtaUpdateScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.FieldData.route) { 
                com.kavach.app.ui.screens.field.FieldDataScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AuditCenter.route) { androidx.compose.material3.Text("Audit Center Screen Placeholder") }

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
                arguments = listOf(navArgument("trainingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val trainingId = backStackEntry.arguments?.getString("trainingId") ?: return@composable
                VideoPlayerScreen(
                    trainingId      = trainingId,
                    onVideoComplete = { navController.navigate(Screen.Quiz.createRoute(trainingId)) }
                )
            }

            composable(
                route = Screen.Quiz.route,
                arguments = listOf(navArgument("trainingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val trainingId = backStackEntry.arguments?.getString("trainingId") ?: return@composable
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
                    navArgument("trainingId") { type = NavType.StringType },
                    navArgument("score")      { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val trainingId = backStackEntry.arguments?.getString("trainingId") ?: return@composable
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
    }

    // Loading Overlay for pending Role resolution
        if (!token.isNullOrBlank() && role.isNullOrBlank()) {
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


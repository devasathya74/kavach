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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Text
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.ui.screens.broadcast.BroadcastInboxScreen
import com.kavach.app.ui.screens.broadcast.CreateBroadcastScreen
import com.kavach.app.ui.screens.common.ComingSoonScreen
import com.kavach.app.ui.screens.dashboard.CommandCenterScreen
import com.kavach.app.ui.screens.dashboard.FieldDashboardScreen
import com.kavach.app.ui.screens.dashboard.PilotDashboardScreen
import com.kavach.app.ui.screens.dashboard.RestrictedDashboardScreen
import com.kavach.app.ui.screens.login.ConsentScreen
import com.kavach.app.ui.screens.login.LoginScreen
import com.kavach.app.ui.screens.security.SecurityEnrollmentScreen
import com.kavach.app.ui.screens.security.SecureAccessScreen
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
import com.kavach.app.ui.overlay.OverlayViewModel
import com.kavach.app.ui.overlay.TacticalOverlaySystem
import com.kavach.app.core.security.BiometricAuthManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber

/**
 * Root navigation host with role-based routing.
 *
 * Role resolution order (priority):
 *  1. LocalTestBypass (if ENABLED and PNO matches known test PNOs)
 *  2. RoleRouter.normalize(storedRole) — normalizes any backend string
 *  3. Falls back to "USER" (field dashboard) if unknown
 */
@Composable
fun KavachNavHost(
    navController     : NavHostController = rememberNavController(),
    viewModel         : NavHostViewModel  = hiltViewModel(),
    authorizationEngine: com.kavach.app.core.security.AuthorizationEngine = viewModel.authorizationEngine,
    onUserInteraction : () -> Unit        = {}
) {
    val biometricAuthManager = viewModel.biometricAuthManager
    val sessionDataStore   = viewModel.sessionDataStore
    val scope              = rememberCoroutineScope()

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val breachReason by viewModel.sessionBreachReason.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState(initial = ConnectionStatus.AVAILABLE)
    val lastSyncTime by sessionDataStore.lastAuthTime.collectAsState(initial = 0L)

    val overlayViewModel : OverlayViewModel = hiltViewModel()
    val overlayThreat    by overlayViewModel.threatLevel.collectAsStateWithLifecycle()
    val overlayCommand   by overlayViewModel.pendingCommand.collectAsStateWithLifecycle()
    val overlayLockdown  by overlayViewModel.isLockdown.collectAsStateWithLifecycle()
    val overlayEmergency by overlayViewModel.emergencyBroadcast.collectAsStateWithLifecycle()
    val overlayUplink    by overlayViewModel.uplinkDegraded.collectAsStateWithLifecycle()

    val pendingAuthChallenge by authorizationEngine.pendingChallenge.collectAsStateWithLifecycle()
    val startupTimeline by viewModel.startupTimeline.collectAsStateWithLifecycle()

    // ── Single Source of Truth Navigation ────────────────────
    LaunchedEffect(authState) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        
        when (val state = authState) {
            is AuthState.NeedsConsent -> {
                if (currentRoute != Screen.Consent.route) {
                    navController.navigate(Screen.Consent.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.NeedsPermissions -> {
                if (currentRoute != Screen.PermissionGate.route) {
                    navController.navigate(Screen.PermissionGate.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                // If we are on any screen other than Login/Splash, go to Login
                if (currentRoute != Screen.Login.route && 
                    currentRoute != Screen.Splash.route) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            is AuthState.NeedsSecurityEnrollment -> {
                if (currentRoute != Screen.SecurityEnrollment.route) {
                    navController.navigate(Screen.SecurityEnrollment.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
            is AuthState.Authenticated -> {
                // Resolve dashboard route based on role
                val dashRoute = RoleRouter.dashboardRoute(
                    RoleRouter.LocalTestBypass.resolveRole(state.pno) ?: RoleRouter.normalize(state.role)
                )
                
                if (currentRoute == Screen.Login.route ||
                    currentRoute == Screen.Splash.route ||
                    currentRoute == Screen.SecurityEnrollment.route ||
                    currentRoute == Screen.Consent.route ||
                    currentRoute == Screen.PermissionGate.route) {
                    
                    navController.navigate(dashRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is AuthState.StartupTimeout -> {
                // Stay on current screen but UI will show error overlay
            }
            else -> {} 
        }
    }

    // ── Background Sync ──────────────────────────────────────
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            kotlinx.coroutines.delay(15_000)
            viewModel.syncProfile(silent = true)
        }
    }

    if (authState is AuthState.Loading) {
        Box(modifier = Modifier.fillMaxSize()
            .background(com.kavach.app.ui.theme.NavyBlueDark),
            contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                color = com.kavach.app.ui.theme.GoldenYellow)
        }
        return
    }

    // ── Point 9 & 10 FIX: Lock Screen Isolation ─────────────────
    if (authState is AuthState.AppLocked) {
        SecureAccessScreen(biometricManager = biometricAuthManager)
        return
    }

    if (authState is AuthState.StartupTimeout) {
        Box(modifier = Modifier.fillMaxSize().background(com.kavach.app.ui.theme.NavyBlueDark),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("⚠️ स्टार्टअप समय समाप्त (Timeout)", color = com.kavach.app.ui.theme.GoldenYellow, fontWeight = FontWeight.Bold)
                Text("सर्वर से संपर्क नहीं हो पा रहा है।", color = com.kavach.app.ui.theme.OnSurfaceMid, fontSize = 14.sp)
                
                Spacer(Modifier.height(24.dp))
                
                // ── Point 8 FIX: Startup Debug Timeline ──
                Text("BOOT CHAIN DIAGNOSTICS", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                startupTimeline.forEach { (stage, success) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (success) "✓" else "✗", color = if (success) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(stage, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                androidx.compose.material3.Button(onClick = { viewModel.manualRetry() }) {
                    Text("पुनः प्रयास करें")
                }
            }
        }
        return
    }

    // ── Point 11 FIX: Sensitive Action Challenge Overlay ────────
    if (pendingAuthChallenge != null) {
        val challenge = pendingAuthChallenge!!
        val context = androidx.compose.ui.platform.LocalContext.current
        val activity = context as? androidx.fragment.app.FragmentActivity
        
        if (challenge.requiresBio && activity != null) {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            
            DisposableEffect(challenge) {
                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            // Point 4 FIX: Check for dead activity
                            if (activity.isFinishing || activity.isDestroyed) {
                                Timber.w("Biometric result arrived but activity is dead. Aborting.")
                                return
                            }
                            authorizationEngine.resolveChallenge(true)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                                authorizationEngine.resolveChallenge(false)
                            }
                        }
                    })

                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                        biometricPrompt.cancelAuthentication()
                        authorizationEngine.cancelChallenge()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("प्रमाणीकरण आवश्यक")
                    .setSubtitle(challenge.action.label)
                    .setNegativeButtonText("रद्द करें")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                
                biometricPrompt.authenticate(promptInfo)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
        }
    }

    TacticalOverlaySystem(
        threatLevel            = overlayThreat,
        pendingCommand         = overlayCommand,
        isLockdown             = overlayLockdown,
        emergencyBroadcast     = overlayEmergency,
        uplinkDegraded         = overlayUplink,
        onCommandAcknowledged  = { overlayViewModel.acknowledgeCommand() },
        onLockdownAcknowledged = { overlayViewModel.acknowledgeLockdown() },
        onBroadcastDismissed   = { overlayViewModel.dismissEmergencyBroadcast() }
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ConnectivityBanner(
                status = connectionStatus,
                lastSyncedMs = if (lastSyncTime > 0) lastSyncTime else null
            )



            NavHost(
                navController    = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) { awaitPointerEvent(); onUserInteraction() }
                    }
                }
            ) {

                // ── Splash ──────────────────────────────────────
                composable(Screen.Splash.route) {
                    com.kavach.app.ui.screens.splash.SplashScreen(
                        onBootComplete = {
                            // No manual navigation here!
                            // AuthState Started flow will handle it.
                        },
                        onCompromised = {
                            navController.navigate(Screen.Restricted.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

                // ── Connectivity Diagnostic ─────────────────────
                composable(Screen.ConnectivityTest.route) {
                    com.kavach.app.ui.screens.pilot.ConnectivityScreen(
                        onSuccess = { navController.popBackStack() }
                    )
                }

                // ── Consent ────────────────────────────────────
                composable(Screen.Consent.route) {
                    ConsentScreen(
                        onAccepted = {
                            scope.launch {
                                sessionDataStore.saveConsent()
                                // No manual navigate! AuthState will update.
                            }
                        }
                    )
                }

                // ── Permissions ─────────────────────────────────
                composable(Screen.PermissionGate.route) {
                    com.kavach.app.ui.screens.permissions.PermissionGateScreen(
                        onContinue = {
                            scope.launch {
                                sessionDataStore.savePermissionsHandled()
                                // No manual navigate! AuthState will update.
                            }
                        }
                    )
                }

                // ── Login ───────────────────────────────────────
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = { 
                            // After login, AuthState will update to NeedsSecurityEnrollment or Authenticated
                        },
                        onAdminLoggedIn = {
                            // Unified login now
                        },
                        onDiagnosticsClick = {
                            navController.navigate(Screen.PilotDiagnostics.route)
                        }
                    )
                }

                composable(Screen.SecurityEnrollment.route) {
                    SecurityEnrollmentScreen(
                        onComplete = {
                            // AuthState will become Authenticated
                        }
                    )
                }

                composable(Screen.PilotDiagnostics.route) {
                    com.kavach.app.ui.screens.pilot.PilotDiagnosticsScreen(
                        onClose = { navController.popBackStack() }
                    )
                }

                // OtpVerify is removed




                // ── Restricted ──────────────────────────────────
                composable(Screen.Restricted.route) {
                    RestrictedDashboardScreen(
                        onRetry  = { viewModel.manualRetry() },
                        onLogout = {
                            viewModel.logout()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                // ── ROLE DASHBOARDS ─────────────────────────────

                // Unified Dashboard (Role-based content visibility inside)
                composable(Screen.Dashboard.route) {
                    val role = (authState as? AuthState.Authenticated)?.role ?: "USER"
                    com.kavach.app.ui.screens.dashboard.UnifiedDashboardScreen(
                        role = RoleRouter.normalize(role),
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }


                // ── Personnel ───────────────────────────────────
                composable(Screen.UserManagement.route) {
                    PersonnelListScreen(
                        onUserClick = { userId ->
                            navController.navigate(Screen.UserDetail.createRoute(userId))
                        },
                        onAddUser = {
                            navController.navigate(Screen.UserRegistration.createRoute()) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = Screen.UserDetail.route,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType })
                ) {
                    OfficerDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEditUser = { userId ->
                            navController.navigate(Screen.UserRegistration.createRoute(userId)) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = Screen.UserRegistration.route,
                    arguments = listOf(
                        navArgument("userId") { 
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) {
                    com.kavach.app.ui.screens.pilot.personnel.UserRegistrationScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.PendingApprovals.route) {
                    ApprovalListScreen(onBack = { navController.popBackStack() })
                }

                // ── Incidents ───────────────────────────────────
                composable(Screen.IncidentCenter.route) {
                    com.kavach.app.ui.screens.pilot.incident.IncidentCenterScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Broadcast ───────────────────────────────────
                composable(Screen.BroadcastInbox.route) {
                    BroadcastInboxScreen(onBack = { navController.popBackStack() })
                }

                composable(Screen.BroadcastCenter.route) {
                    com.kavach.app.ui.screens.pilot.broadcast.BroadcastCenterScreen(
                        onBack = { navController.popBackStack() },
                        onCreateBroadcast = { navController.navigate(Screen.CreateBroadcast.route) }
                    )
                }

                composable(Screen.CreateBroadcast.route) {
                    CreateBroadcastScreen(onBack = { navController.popBackStack() })
                }

                // ── Device & OTA ────────────────────────────────
                composable(Screen.DeviceMonitor.route) {
                    com.kavach.app.ui.screens.device.DeviceStatusScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.OtaUpdate.route) {
                    com.kavach.app.ui.screens.pilot.ota.OtaUpdateScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Field Data ──────────────────────────────────
                composable(Screen.FieldData.route) {
                    com.kavach.app.ui.screens.pilot.data.FieldDataScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Audit ───────────────────────────────────────
                composable(Screen.AuditCenter.route) {
                    com.kavach.app.ui.screens.pilot.audit.AuditTimelineScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Live Broadcast ──────────────────────────────
                composable(Screen.LiveBroadcast.route) {
                    com.kavach.app.ui.screens.pilot.live.LiveBroadcastScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Training ────────────────────────────────────
                composable(Screen.TrainingList.route) {
                    TrainingListScreen(
                        onTrainingClick = { id ->
                            navController.navigate(Screen.VideoPlayer.createRoute(id))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.VideoPlayer.route,
                    arguments = listOf(navArgument("trainingId") { type = NavType.StringType })
                ) { back ->
                    val id = back.arguments?.getString("trainingId") ?: return@composable
                    VideoPlayerScreen(
                        trainingId      = id,
                        onVideoComplete = { navController.navigate(Screen.Quiz.createRoute(id)) }
                    )
                }

                composable(
                    route = Screen.Quiz.route,
                    arguments = listOf(navArgument("trainingId") { type = NavType.StringType })
                ) { back ->
                    val id = back.arguments?.getString("trainingId") ?: return@composable
                    QuizScreen(
                        trainingId = id,
                        onSubmit   = { score ->
                            navController.navigate(Screen.QuizResult.createRoute(id, score)) {
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
                ) {
                    QuizResultScreen(
                        onContinue = {
                            // Redirect handled by AuthState (already authenticated)
                            // or manual back stack clearing
                            navController.popBackStack(Screen.Dashboard.route, false)
                        }
                    )
                }

                // ── Orders ──────────────────────────────────────
                composable(Screen.OrderList.route) {
                    OrderListScreen(
                        onOrderClick = { id ->
                            navController.navigate(Screen.OrderDetail.createRoute(id))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.OrderDetail.route,
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                ) {
                    OrderDetailScreen(onBack = { navController.popBackStack() })
                }

                // ── Profile ─────────────────────────────────────
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onLogout = {
                            viewModel.logout()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── ComingSoon (parametric placeholder) ─────────
                // All modules with pending backend integration route here
                composable(
                    route = Screen.ComingSoon.route,
                    arguments = listOf(navArgument("title") { type = NavType.StringType })
                ) { back ->
                    val title = back.arguments?.getString("title") ?: "Module"
                    ComingSoonScreen(
                        title = title,
                        description = "This module is architecturally complete. " +
                            "Backend integration is required before field deployment.",
                        onBack = { navController.popBackStack() }
                    )
                }

            } // NavHost
        }
    }
    } // TacticalOverlaySystem

    // ── Session Breach Dialog ────────────────────────────────────
    if (breachReason != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            containerColor = com.kavach.app.ui.theme.Surface1,
            title = {
                Text("🛡️ सुरक्षा चेतावनी: सत्र समाप्त",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("आपका सत्र समाप्त कर दिया गया है।",
                        color = com.kavach.app.ui.theme.OnSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("कारण:", color = com.kavach.app.ui.theme.GoldenYellow, fontSize = 14.sp)
                    Text("• $breachReason", color = com.kavach.app.ui.theme.OnSurfaceMid, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("कृपया पुनः लॉगिन करें।", color = com.kavach.app.ui.theme.OnSurface)
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.kavach.app.ui.theme.GoldenYellow)
                ) {
                    Text("लॉगिन पर वापस जाएं", color = com.kavach.app.ui.theme.NavyBlueDark)
                }
            }
        )
    }
}

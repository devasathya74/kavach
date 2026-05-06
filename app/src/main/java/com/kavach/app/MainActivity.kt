package com.kavach.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.UpdateInfoDto
import com.kavach.app.ui.navigation.KavachNavHost
import com.kavach.app.ui.theme.KavachTheme
import com.kavach.app.util.AutoUpdateManager
import com.kavach.app.util.SecurityUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionDataStore: SessionDataStore
    @Inject lateinit var autoUpdateManager: AutoUpdateManager
    @Inject lateinit var apiService: KavachApiService
    @Inject lateinit var navigationDao: com.kavach.app.data.local.dao.NavigationDao

    private var showSettingsDialog by mutableStateOf(false)
    private var showPermissionExplanation by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            if (!granted) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showSettingsDialog = true
                }
            }
        }

    private fun requestPermissionsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    // Show explanation first
                    showPermissionExplanation = true
                }
                else -> {
                    // First time or permanently denied (Rationale will be false if permanently denied)
                    // If we haven't asked yet, show explanation
                    showPermissionExplanation = true
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        requestPermissionsIfNeeded()
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        performStartupSecurityChecks()

        setContent {
            KavachTheme {
                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = { Text("Permission Required") },
                        text = { Text("Notifications are required for important alerts. Please enable them in settings.") },
                        confirmButton = {
                            TextButton(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.fromParts("package", packageName, null)
                                startActivity(intent)
                                showSettingsDialog = false
                            }) { Text("Settings") }
                        }
                    )
                }

                if (showPermissionExplanation) {
                    AlertDialog(
                        onDismissRequest = { showPermissionExplanation = false },
                        title = { Text("महत्वपूर्ण सूचना (Notification)") },
                        text = { Text("KAVACH को आपातकालीन अलर्ट भेजने के लिए नोटिफिकेशन की अनुमति आवश्यक है।\n\nNotifications are required for real-time emergency alerts.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showPermissionExplanation = false
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }) { Text("अनुमति दें (Allow)") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionExplanation = false }) { Text("अभी नहीं") }
                        }
                    )
                }

                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                var updateInfo by remember { mutableStateOf<UpdateInfoDto?>(null) }
                
                // 1. Mission-Grade Queue Draining (Single Source: Room)
                LaunchedEffect(Unit) {
                    navigationDao.getUnprocessedQueue().collect { queue ->
                        queue.forEach { entity ->
                            scope.launch {
                                // ATOMIC ACQUIRE: mark as processing in a transaction
                                val acquired = navigationDao.acquireIntent(entity.notifId)
                                if (acquired != null) {
                                    // VALIDATION: Check if intent is still valid (placeholder for API check)
                                    val isValid = validateIntent(acquired) 
                                    if (isValid) {
                                        navController.navigate(acquired.screen) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    // ATOMIC MARK: always mark as processed even if skipped (invalid)
                                    navigationDao.markProcessed(acquired.notifId)
                                }
                            }
                        }
                    }
                }

                // 2. Cleanup Job (Keep DB lean)
                LaunchedEffect(Unit) {
                    while(true) {
                        navigationDao.cleanup(System.currentTimeMillis() - 86400000) // 24hr expiry
                        kotlinx.coroutines.delay(3600000) // Every hour
                    }
                }

                KavachNavHost(navController = navController)

                LaunchedEffect(Unit) {
                    // Report Notification Permission status
                    scope.launch {
                        val enabled = NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                        try {
                            apiService.logUpdateEvent(mapOf(
                                "event" to "PERMISSION_CHECK",
                                "metadata" to mapOf("notifications_enabled" to enabled)
                            ))
                        } catch (e: Exception) { }
                    }

                    // Update logic
                    scope.launch {
                        sessionDataStore.pendingForceUpdate.collect { isBlocked ->
                            if (isBlocked && updateInfo == null) {
                                updateInfo = autoUpdateManager.checkUpdate()
                            }
                        }
                    }

                    scope.launch {
                        val info = autoUpdateManager.checkUpdate()
                        updateInfo = info
                        if (info?.forceUpdate == true) {
                            sessionDataStore.setPendingForceUpdate(true)
                        } else {
                            sessionDataStore.setPendingForceUpdate(false)
                        }
                    }
                }

                updateInfo?.let { info ->
                    androidx.activity.compose.BackHandler(enabled = info.forceUpdate) { }

                    AlertDialog(
                        onDismissRequest = { if (!info.forceUpdate) updateInfo = null },
                        title   = { Text("🚀 मिशन-क्रिटिकल अपडेट (v${info.versionCode})") },
                        text    = { 
                            androidx.compose.foundation.layout.Column {
                                Text(info.releaseNotes)
                                if (info.forceUpdate) {
                                    Text("\nयह अपडेट सुरक्षा के लिए अनिवार्य है।", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = { autoUpdateManager.downloadAndInstall(info) }) {
                                Text("अभी अपडेट करें")
                            }
                        },
                        dismissButton = {
                            if (!info.forceUpdate) {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("बाद में")
                                }
                            }
                        },
                        properties = androidx.compose.ui.window.DialogProperties(
                            dismissOnBackPress = !info.forceUpdate,
                            dismissOnClickOutside = !info.forceUpdate
                        )
                    )
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        val targetScreen = intent.getStringExtra("screen")
        val notifId = intent.getStringExtra("notif_id") ?: "INTERNAL_${System.currentTimeMillis()}"
        val priority = intent.getIntExtra("priority", 0)

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Persistent Dedup & Priority Capping
            if (!navigationDao.isHandled(notifId)) {
                navigationDao.insertWithCapping(
                    com.kavach.app.data.local.entity.PendingNavigationEntity(
                        notifId = notifId,
                        screen = targetScreen ?: "",
                        timestamp = System.currentTimeMillis(),
                        priority = priority
                    )
                )
            }

            // 2. Acknowledgement (Non-blocking)
            if (intent.hasExtra("notif_id")) {
                try {
                    apiService.sendNotificationAck(mapOf("notif_id" to notifId, "type" to "OPENED"))
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to send ACK: ${e.message}")
                }
            }
        }
    }

    private suspend fun validateIntent(entity: com.kavach.app.data.local.entity.PendingNavigationEntity): Boolean {
        // Rule 1: Stale check (e.g. 24 hours old)
        if (System.currentTimeMillis() - entity.timestamp > 86400000) return false
        
        // Rule 2: Screen safety
        if (entity.screen.isEmpty()) return false
        
        // Rule 3: Priority Check (Critical always passes validation unless stale)
        if (entity.priority == 1) return true
        
        return true // Default pass for MVP
    }

    private fun performStartupSecurityChecks() {
        if (!SecurityUtils.isEnvironmentSafe(this)) {
            finish()
        }
    }
}

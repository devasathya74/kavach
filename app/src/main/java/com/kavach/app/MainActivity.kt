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
import androidx.compose.runtime.collectAsState
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
import com.kavach.app.data.remote.dto.system.UpdateInfoDto
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





    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setTitle("")
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        performStartupSecurityChecks()

        setContent {
            KavachTheme {

                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val token by sessionDataStore.token.collectAsState(initial = null)
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
                    // Report Notification Permission status — only after login (token exists)
                    // Sending this before login hits the backend as anonymous → 403
                    scope.launch {
                        val currentToken = sessionDataStore.token.firstOrNull()
                        if (!currentToken.isNullOrBlank()) {
                            val enabled = NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                            try {
                                apiService.logUpdateEvent(mapOf(
                                    "event" to "PERMISSION_CHECK",
                                    "metadata" to mapOf("notifications_enabled" to enabled)
                                ))
                            } catch (e: Exception) { }
                        }
                    }
                }

                // ── Mission-Grade OTA Lifecycle ─────────────────
                // connectivity check (done in NavHost) -> session validation (token) -> update check
                LaunchedEffect(token) {
                    if (!token.isNullOrBlank()) {
                        scope.launch {
                            val info = autoUpdateManager.checkUpdate()
                            if (info != null) {
                                updateInfo = info
                                if (com.kavach.app.KavachConfig.PILOT_MODE) {
                                    sessionDataStore.setPendingForceUpdate(false)
                                } else if (info.forceUpdate || info.versionCode < info.minSupportedVersion) {
                                    sessionDataStore.setPendingForceUpdate(true)
                                } else {
                                    sessionDataStore.setPendingForceUpdate(false)
                                }
                                
                                // Log for audit
                                try {
                                    apiService.logUpdateEvent(mapOf(
                                        "event" to "UPDATE_PROMPT_SHOWN",
                                        "metadata" to mapOf(
                                            "version" to info.versionCode,
                                            "is_rollback" to info.isRollback,
                                            "channel" to info.channel
                                        )
                                    ))
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }

                val updateStatus by autoUpdateManager.updateStatus.collectAsState()

                // ── Update Lifecycle Dialogs ─────────────────
                when (val status = updateStatus) {
                    is AutoUpdateManager.UpdateStatus.Downloading -> {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("🚀 अपडेट डाउनलोड हो रहा है...") },
                            text = { Text("कृपया प्रतीक्षा करें, सुरक्षित अपडेट डाउनलोड किया जा रहा है।") },
                            confirmButton = {}
                        )
                    }
                    is AutoUpdateManager.UpdateStatus.Verifying -> {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("🛡️ सुरक्षा सत्यापन (Verifying)...") },
                            text = { Text("अपडेट की अखंडता और सिग्नेचर की जाँच की जा रही है।") },
                            confirmButton = {}
                        )
                    }
                    is AutoUpdateManager.UpdateStatus.IncompatibleRollback -> {
                        AlertDialog(
                            onDismissRequest = { /* Hard block */ },
                            title = { Text("🚫 रोलबैक बाधित (Incompatible)") },
                            text = { Text("यह रोलबैक आपके वर्तमान डेटाबेस (v${status.current}) के साथ संगत नहीं है (Target: v${status.target})।\n\nकृपया अपने कमांडिंग ऑफिसर से संपर्क करें।") },
                            confirmButton = {
                                TextButton(onClick = { /* Force close or similar */ }) { Text("OK") }
                            }
                        )
                    }
                    is AutoUpdateManager.UpdateStatus.Error -> {
                        AlertDialog(
                            onDismissRequest = { /* Allow retry if not forced */ },
                            title = { Text("❌ अपडेट विफल") },
                            text = { Text(status.message) },
                            confirmButton = {
                                TextButton(onClick = { 
                                    updateInfo?.let { autoUpdateManager.downloadAndInstall(it) } 
                                }) { Text("फिर से प्रयास करें") }
                            },
                            dismissButton = {
                                TextButton(onClick = { /* Handle cancel */ }) { Text("बंद करें") }
                            }
                        )
                    }
                    else -> {}
                }

                updateInfo?.let { info ->
                    val isPilot = com.kavach.app.KavachConfig.PILOT_MODE
                    val forceUpdate = if (isPilot) false else info.forceUpdate

                    androidx.activity.compose.BackHandler(enabled = forceUpdate) { }

                    AlertDialog(
                        onDismissRequest = { if (!forceUpdate) updateInfo = null },
                        title   = { 
                            Text(if (info.isRollback) "🛡️ सुरक्षा रोलबैक (v${info.versionCode})" 
                                 else "🚀 मिशन-क्रिटिकल अपडेट (v${info.versionCode})") 
                        },
                        text    = { 
                            androidx.compose.foundation.layout.Column {
                                Text(info.releaseNotes ?: "")
                                if (info.isRollback) {
                                    Text("\nसिस्टम को पिछली सुरक्षित स्थिति में वापस लाया जा रहा है।", 
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                }
                                if (info.forceUpdate || info.versionCode < info.minSupportedVersion) {
                                    Text("\nयह अपडेट सुरक्षा के लिए अनिवार्य है।", 
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                                }
                                if (info.isCritical) {
                                    Text("\n⚠️ यह एक अत्यंत महत्वपूर्ण सुरक्षा पैच है।", 
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = { autoUpdateManager.downloadAndInstall(info) }) {
                                Text("अभी अपडेट करें")
                            }
                        },
                        dismissButton = {
                            if (!forceUpdate) {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("बाद में")
                                }
                            }
                        },
                        properties = androidx.compose.ui.window.DialogProperties(
                            dismissOnBackPress = !forceUpdate,
                            dismissOnClickOutside = !forceUpdate
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
        if (!com.kavach.app.KavachConfig.PILOT_MODE) {
            if (!SecurityUtils.isEnvironmentSafe(this)) {
                android.util.Log.e("KavachSecurity", "SECURITY FAILURE: Environment not safe. Haling.")
                finish()
            }
        } else {
            android.util.Log.w("KavachSecurity", "PILOT MODE ENABLED: Skipping strict security checks.")
        }
    }
}

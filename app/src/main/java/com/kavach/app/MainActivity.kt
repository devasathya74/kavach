package com.kavach.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.navigation.compose.rememberNavController
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.UpdateInfoDto
import com.kavach.app.ui.navigation.KavachNavHost
import com.kavach.app.ui.theme.KavachTheme
import com.kavach.app.util.AutoUpdateManager
import com.kavach.app.util.SecurityUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionDataStore: SessionDataStore
    @Inject lateinit var autoUpdateManager: AutoUpdateManager
    @Inject lateinit var apiService: KavachApiService

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        performStartupSecurityChecks()

        setContent {
            KavachTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                var updateInfo by remember { mutableStateOf<UpdateInfoDto?>(null) }
                
                val currentIntent = (LocalContext.current as? MainActivity)?.intent
                val targetScreen = currentIntent?.getStringExtra("screen")
                val notifId = currentIntent?.getStringExtra("notif_id")
                
                LaunchedEffect(targetScreen, notifId) {
                    if (!targetScreen.isNullOrEmpty()) {
                        navController.navigate(targetScreen)
                        currentIntent?.removeExtra("screen")
                    }
                    if (!notifId.isNullOrEmpty()) {
                        scope.launch {
                            try {
                                apiService.sendNotificationAck(mapOf("notif_id" to notifId, "type" to "OPENED"))
                                currentIntent?.removeExtra("notif_id")
                            } catch (e: Exception) { /* Silent fail */ }
                        }
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

    private fun performStartupSecurityChecks() {
        if (!SecurityUtils.isEnvironmentSafe(this)) {
            finish()
        }
    }
}

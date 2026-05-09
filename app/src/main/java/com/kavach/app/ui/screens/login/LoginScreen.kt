package com.kavach.app.ui.screens.login

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.R
import com.kavach.app.ui.theme.*

/**
 * Login Screen
 *
 * दो मोड: ① Normal Officer — PNO enter करो → OTP भेजो → OTP verify ② Admin Officer — PNO + Password
 * → Direct AdminDashboard
 *
 * Admin mode: "Admin Login" link पर tap करने से toggle होता है।
 */
@Composable
fun LoginScreen(
        onLoginSuccess: (String) -> Unit, // OTP flow के लिए → OtpVerifyScreen
        onAdminLoggedIn: () -> Unit, // Admin direct login के लिए → AdminDashboard
        onDiagnosticsClick: () -> Unit = {}, // 5-Tap Trick के लिए → DiagnosticsScreen
        viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.loginState.collectAsStateWithLifecycle()
    val adminState by viewModel.adminLoginState.collectAsStateWithLifecycle()

    var pno by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Officer OTP flow navigate
    LaunchedEffect(state.otpSent) { if (state.otpSent) onLoginSuccess(pno) }

    // Admin direct login navigate
    LaunchedEffect(adminState.loggedIn) { if (adminState.loggedIn) onAdminLoggedIn() }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            var tapCount by remember { mutableIntStateOf(0) }
            // ── Icon ─────────────────────────────────────
            Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                            Modifier.size(96.dp).clickable {
                                tapCount++
                                if (tapCount >= 5) {
                                    tapCount = 0
                                    if (com.kavach.app.BuildConfig.DEBUG) {
                                        onDiagnosticsClick()
                                    }
                                }
                            }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
                    textAlign = TextAlign.Center
            )

            // ── PNO Input ─────────────────────────────────
            OutlinedTextField(
                    value = pno,
                    onValueChange = {
                        if (it.length <= 9 && it.all { char -> char.isDigit() }) {
                            pno = it
                        }
                    },
                    label = { Text("Officer ID (PNO)") },
                    leadingIcon = {
                        Icon(
                                Icons.Filled.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions =
                            KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                            ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password Field (Optional for Admin) ──────────
            OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("पासवर्ड / Password") },
                    leadingIcon = {
                        Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                    imageVector =
                                            if (showPassword) Icons.Filled.VisibilityOff
                                            else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation =
                            if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                    keyboardOptions =
                            KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                            ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
            )

            // ── Error Message ──────────────────────────────
            val errorMsg = state.error ?: adminState.error
            AnimatedVisibility(visible = errorMsg != null) {
                Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Action Buttons ─────────────────────────────
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Password Button
                Button(
                        onClick = { viewModel.adminLogin(pno, password) },
                        enabled = pno.length == 9 && password.length >= 3 && !adminState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                ) {
                    if (adminState.isLoading) {
                        CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Login", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private val Transparent = androidx.compose.ui.graphics.Color.Transparent

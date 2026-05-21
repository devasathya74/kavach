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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.R
import com.kavach.app.ui.theme.*

/**
 * Login Screen
 *
 * दो मोड: ① Normal Officer — PNO enter करो → OTP भेजो → OTP verify ② Admin Officer — PNO + Password
 * → Direct AdminDashboard
 * Admin mode: "Admin Login" link
 */
@Composable
fun LoginScreen(
        onLoginSuccess: () -> Unit,
        onDiagnosticsClick: () -> Unit = {},
        onAdminLoggedIn: () -> Unit = {},
        viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.loginState.collectAsStateWithLifecycle()

    var pno by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoginSuccess() }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .background(NavyBlueDark)
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            var tapCount by remember { mutableIntStateOf(0) }
            
            // -- Tactical Header --
            Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = GoldenYellow,
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
                    text = "KAVACH",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )

            Text(
                    text = "SECURE TACTICAL OPERATIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldenYellow.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 48.dp),
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
                    label = { Text("Officer ID (PNO)", color = OnSurfaceMid) },
                    leadingIcon = {
                        Icon(
                                Icons.Filled.Badge,
                                contentDescription = null,
                                tint = GoldenYellow
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    keyboardOptions =
                            KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                            ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password Field ───────────────────────────
            OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Operational Password", color = OnSurfaceMid) },
                    leadingIcon = {
                        Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = GoldenYellow
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                    imageVector =
                                            if (showPassword) Icons.Filled.VisibilityOff
                                            else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = OnSurfaceMid
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
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
            AnimatedVisibility(visible = state.error != null) {
                Text(
                        text = state.error ?: "",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Login Button ──────────────────────────────
            Button(
                    onClick = { viewModel.login(pno, password) },
                    enabled = pno.length == 9 && password.isNotBlank() && !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = GoldenYellow,
                                    contentColor = NavyBlueDark,
                                    disabledContainerColor = GoldenYellow.copy(alpha = 0.3f)
                            )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                            color = NavyBlueDark,
                            modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("AUTHORIZE ACCESS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val Transparent = androidx.compose.ui.graphics.Color.Transparent

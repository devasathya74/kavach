package com.kavach.app.ui.screens.login

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
 * दो मोड:
 *  ① Normal Officer  — PNO enter करो → OTP भेजो → OTP verify
 *  ② Admin Officer   — PNO + Password → Direct AdminDashboard
 *
 * Admin mode: "Admin Login" link पर tap करने से toggle होता है।
 */
@Composable
fun LoginScreen(
    onLoginSuccess    : (String) -> Unit,    // OTP flow के लिए → OtpVerifyScreen
    onAdminLoggedIn   : () -> Unit,           // Admin direct login के लिए → AdminDashboard
    viewModel         : LoginViewModel = hiltViewModel()
) {
    val state      by viewModel.loginState.collectAsStateWithLifecycle()
    val adminState by viewModel.adminLoginState.collectAsStateWithLifecycle()

    var pno          by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var isAdminMode  by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    // Officer OTP flow navigate
    LaunchedEffect(state.otpSent) {
        if (state.otpSent) onLoginSuccess(pno)
    }

    // Admin direct login navigate
    LaunchedEffect(adminState.loggedIn) {
        if (adminState.loggedIn) onAdminLoggedIn()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyBlueDark, NavyBlue, Surface1)
                )
            )
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Icon ─────────────────────────────────────
            AnimatedContent(targetState = isAdminMode, label = "icon_anim") { admin ->
                Icon(
                    imageVector        = if (admin) Icons.Filled.AdminPanelSettings else Icons.Filled.Security,
                    contentDescription = null,
                    tint               = GoldenYellow,
                    modifier           = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text  = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = OnSurface
            )

            AnimatedContent(targetState = isAdminMode, label = "subtitle_anim") { admin ->
                Text(
                    text  = if (admin) "कमांड एक्सेस — Admin पोर्टल" else stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (admin) GoldenYellow else OnSurfaceMid,
                    modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
                    textAlign = TextAlign.Center
                )
            }

            // ── Mode Tab ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Surface1.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Officer Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (!isAdminMode) GoldenYellow else Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { isAdminMode = false; password = "" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "अधिकारी",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (!isAdminMode) NavyBlueDark else OnSurfaceMid
                    )
                }
                // Admin Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isAdminMode) GoldenYellow else Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { isAdminMode = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "Admin",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAdminMode) NavyBlueDark else OnSurfaceMid
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── PNO Input ─────────────────────────────────
            OutlinedTextField(
                value         = pno,
                onValueChange = { pno = it.uppercase() },
                label         = { Text("PNO (Personnel Number)") },
                leadingIcon   = { Icon(Icons.Filled.Badge, contentDescription = null, tint = GoldenYellow) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction    = if (isAdminMode) ImeAction.Next else ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = GoldenYellow,
                    unfocusedBorderColor = OnSurfaceLow,
                    focusedLabelColor    = GoldenYellow,
                    cursorColor          = GoldenYellow,
                    focusedTextColor     = OnSurface,
                    unfocusedTextColor   = OnSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Password Field (Admin mode only) ──────────
            AnimatedVisibility(
                visible = isAdminMode,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it },
                        label         = { Text("Admin पासवर्ड") },
                        leadingIcon   = { Icon(Icons.Filled.Lock, contentDescription = null, tint = GoldenYellow) },
                        trailingIcon  = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = OnSurfaceMid
                                )
                            }
                        },
                        singleLine    = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = GoldenYellow,
                            unfocusedBorderColor = OnSurfaceLow,
                            focusedLabelColor    = GoldenYellow,
                            cursorColor          = GoldenYellow,
                            focusedTextColor     = OnSurface,
                            unfocusedTextColor   = OnSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Error Message ──────────────────────────────
            val errorMsg = if (isAdminMode) adminState.error else state.error
            AnimatedVisibility(visible = errorMsg != null) {
                Text(
                    text  = errorMsg ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Action Button ─────────────────────────────
            val isLoading = if (isAdminMode) adminState.isLoading else state.isLoading
            Button(
                onClick  = {
                    if (isAdminMode) viewModel.adminLogin(pno, password)
                    else             viewModel.requestOtp(pno)
                },
                enabled  = !isLoading && pno.isNotBlank() && (!isAdminMode || password.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldenYellow,
                    contentColor   = NavyBlueDark
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        text  = if (isAdminMode) "Admin Login करें" else "OTP भेजें",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text      = "केवल अधिकृत कार्मिकों के लिए",
                style     = MaterialTheme.typography.labelSmall,
                color     = OnSurfaceLow,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val Transparent = androidx.compose.ui.graphics.Color.Transparent

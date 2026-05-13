package com.kavach.app.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.kavach.app.ui.theme.*

/**
 * OTP Verification Screen.
 * Receives the PNO from navigation arg and asks for OTP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerifyScreen(
    pno        : String,
    onVerified : () -> Unit,
    viewModel  : LoginViewModel = hiltViewModel()
) {
    val state  by viewModel.otpState.collectAsStateWithLifecycle()
    var otp    by remember { mutableStateOf("") }

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Brush.verticalGradient(listOf(NavyBlueDark, NavyBlue, Surface1)))
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = Icons.Filled.Lock,
                contentDescription = null,
                tint               = GoldenYellow,
                modifier           = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text  = "OTP सत्यापन",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface
            )

            Text(
                text      = "आपके पंजीकृत नंबर पर OTP भेजा गया है",
                style     = MaterialTheme.typography.bodyMedium,
                color     = OnSurfaceMid,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(vertical = 12.dp)
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value         = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                label         = { Text("OTP दर्ज करें") },
                singleLine    = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
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

            AnimatedVisibility(visible = state.error != null) {
                Text(
                    text  = state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = { viewModel.verifyOtp(pno, otp) },
                enabled  = !state.isLoading && otp.length >= 4,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GoldenYellow,
                    contentColor   = NavyBlueDark
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(22.dp))
                } else {
                    Text("सत्यापित करें", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

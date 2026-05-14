package com.kavach.app.ui.screens.security

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.components.TacticalKeypad
import com.kavach.app.ui.theme.*

@Composable
fun SecurityEnrollmentScreen(
    onComplete: () -> Unit,
    viewModel: SecurityEnrollmentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // -- Progress Indicator --
            EnrollmentProgress(state.step)

            Spacer(Modifier.height(48.dp))

            Crossfade(targetState = state.step, label = "step_transition") { step ->
                when (step) {
                    EnrollmentStep.CREATE_PIN -> PinInputView(
                        title = "Tactical PIN सेट करें",
                        subtitle = "सुरक्षित एक्सेस के लिए 6 अंकों का PIN चुनें",
                        pin = state.pin,
                        error = state.error,
                        onDigit = { viewModel.onPinInput(it) },
                        onBackspace = { viewModel.onBackspace() },
                        onNext = { if (state.pin.length == 6) viewModel.nextStep() }
                    )
                    EnrollmentStep.CONFIRM_PIN -> PinInputView(
                        title = "PIN की पुष्टि करें",
                        subtitle = "कृपया अपना 6 अंकों का PIN पुनः दर्ज करें",
                        pin = state.confirmPin,
                        error = state.error,
                        onDigit = { viewModel.onPinInput(it) },
                        onBackspace = { viewModel.onBackspace() },
                        onNext = { if (state.confirmPin.length == 6) viewModel.nextStep() }
                    )
                    EnrollmentStep.SUCCESS -> SuccessView()
                }
            }
        }
    }
}

@Composable
fun EnrollmentProgress(currentStep: EnrollmentStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(active = true)
        StepLine(active = currentStep.ordinal >= 1)
        StepDot(active = currentStep.ordinal >= 1)
        StepLine(active = currentStep.ordinal >= 2)
        StepDot(active = currentStep.ordinal >= 2)
    }
}

@Composable
fun StepDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(if (active) GoldenYellow else OnSurfaceLow, CircleShape)
    )
}

@Composable
fun StepLine(active: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .background(if (active) GoldenYellow else OnSurfaceLow)
    )
}

@Composable
fun PinInputView(
    title: String,
    subtitle: String,
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onNext: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, null, tint = GoldenYellow, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = OnSurfaceMid, fontSize = 14.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))

        // PIN Indicator
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            if (index < pin.length) GoldenYellow else Color.White.copy(alpha = 0.1f),
                            CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        if (error != null) {
            Text(error, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
        }

        // Tactical Keypad
        TacticalKeypad(onDigit, onBackspace)

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNext,
            enabled = pin.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow, contentColor = NavyBlueDark),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("आगे बढ़ें (Continue)", fontWeight = FontWeight.Bold)
        }
    }
}




@Composable
fun SuccessView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.VerifiedUser, null, tint = Color.Green, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(32.dp))
        Text("सुरक्षा सेटअप पूर्ण!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("आपका डिवाइस अब संस्थागत रूप से सुरक्षित है।", color = OnSurfaceMid, fontSize = 16.sp)
        
        Spacer(Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                "TRUSTED DEVICE REGISTERED\nTIMESTAMP: ${System.currentTimeMillis()}",
                color = GoldenYellow.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

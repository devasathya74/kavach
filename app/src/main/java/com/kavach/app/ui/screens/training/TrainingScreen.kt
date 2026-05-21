package com.kavach.app.ui.screens.training

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.OnSurface

/**
 * Entry screen for a training session.
 * Shows title and start button which initiates a locked video playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    trainingId: String,
    viewModel: TrainingScreenViewModel = hiltViewModel(),
    onStart: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training", color = OnSurface) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = viewModel.trainingTitle ?: "Loading...",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { onStart(trainingId) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow)
            ) {
                Text("Start Training", color = NavyBlueDark)
            }
        }
    }
}

// ViewModel placeholder – actual implementation will handle session creation.
class TrainingScreenViewModel : androidx.lifecycle.ViewModel() {
    val trainingTitle: String? = "Sample Training"
}

import androidx.compose.ui.Alignment

package com.kavach.app.ui.screens.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.Training
import com.kavach.app.domain.model.TrainingStatus
import com.kavach.app.ui.theme.*

/**
 * Training list — shows all assigned training videos with status badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingListScreen(
    onTrainingClick : (String) -> Unit,
    onBack          : () -> Unit,
    viewModel       : TrainingListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = { Text("प्रशिक्षण", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = GoldenYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1)
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = GoldenYellow)
            }
            state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.refresh() })
            state.trainings.isEmpty() -> EmptyState("कोई प्रशिक्षण उपलब्ध नहीं")
            else -> LazyColumn(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.trainings, key = { it.id }) { training ->
                    TrainingCard(training = training, onClick = { onTrainingClick(training.id) })
                }
            }
        }
    }
}

@Composable
fun TrainingCard(training: Training, onClick: () -> Unit) {
    val (statusColor, statusText) = when (training.status) {
        TrainingStatus.COMPLETED  -> SuccessGreen  to "पूर्ण"
        TrainingStatus.IN_PROGRESS-> GoldenYellow  to "जारी"
        TrainingStatus.FAILED     -> DangerRed     to "अनुत्तीर्ण"
        TrainingStatus.PENDING    -> OnSurfaceMid  to "लंबित"
    }

    Surface(
        modifier  = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        color     = Surface2,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Filled.PlayCircleFilled,
                contentDescription = null,
                tint               = GoldenYellow,
                modifier           = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text     = training.title,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text  = "${training.duration / 60} मिनट",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMid
                )
                if (training.isMandatory) {
                    Text(
                        text  = "⚠ अनिवार्य",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningOrange
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text     = statusText,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(message, color = OnSurfaceMid, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error, color = DangerRed)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("पुनः प्रयास") }
    }
}

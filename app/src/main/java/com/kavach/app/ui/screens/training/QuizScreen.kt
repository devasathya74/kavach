package com.kavach.app.ui.screens.training

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.QuizQuestion
import com.kavach.app.ui.theme.*

/**
 * Quiz Screen — presents questions with anti-cheat guards:
 *  • Questions are shuffled server-side and locally
 *  • Each answer requires ≥5s reading time
 *  • Max 3 attempts allowed
 *  • Score ≥70% required to pass
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    trainingId : String,
    onSubmit   : (Int) -> Unit,
    viewModel  : QuizViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) {
        state.result?.let { if (it.passed) onSubmit(it.score) }
    }

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title  = { Text("Quiz", color = OnSurface) },
                actions = {
                    // ── Attempt counter badge ─────────────
                    Surface(
                        color = if (state.attemptsLeft <= 1) DangerRed.copy(alpha = 0.2f)
                                else Surface3,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text     = "प्रयास: ${state.attemptsLeft} शेष",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = if (state.attemptsLeft <= 1) DangerRed else OnSurfaceMid,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1)
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = GoldenYellow)
            }
            state.attemptsLeft <= 0 -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Warning, null, tint = DangerRed, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("अधिकतम प्रयास समाप्त", style = MaterialTheme.typography.titleLarge, color = DangerRed)
                    Spacer(Modifier.height(8.dp))
                    Text("Admin से संपर्क करें।", color = OnSurfaceMid, textAlign = TextAlign.Center)
                }
            }
            state.questions.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("कोई प्रश्न नहीं मिला", color = OnSurfaceMid)
            }
            else -> {
                LazyColumn(
                    modifier        = Modifier.fillMaxSize().padding(padding),
                    contentPadding  = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(state.questions) { index, question ->
                        // Notify ViewModel when question becomes visible (for timing)
                        LaunchedEffect(question.id) {
                            viewModel.onQuestionVisible(question.id)
                        }
                        QuizQuestionCard(
                            index          = index,
                            question       = question,
                            selectedOption = state.answers[question.id],
                            onOptionSelect = { option -> viewModel.selectAnswer(question.id, option) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))

                        if (state.error != null) {
                            Surface(
                                color = WarningOrange.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text     = state.error!!,
                                    color    = WarningOrange,
                                    style    = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick  = { viewModel.submitQuiz() },
                            enabled  = !state.isSubmitting && state.answers.size == state.questions.size,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = GoldenYellow,
                                contentColor   = NavyBlueDark
                            )
                        ) {
                            if (state.isSubmitting) CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(22.dp))
                            else Text("जमा करें", style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    index         : Int,
    question      : QuizQuestion,
    selectedOption: String?,
    onOptionSelect: (String) -> Unit
) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        color          = Surface2,
        shape          = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = "प्रश्न ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = GoldenYellow
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = question.question,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Spacer(Modifier.height(14.dp))

            val options = listOf(
                "A" to question.optionA,
                "B" to question.optionB,
                "C" to question.optionC,
                "D" to question.optionD
            )

            options.forEach { (key, text) ->
                val isSelected = selectedOption == key
                val borderColor by animateColorAsState(
                    if (isSelected) GoldenYellow else Divider, label = "border"
                )
                val bgColor by animateColorAsState(
                    if (isSelected) GoldenYellow.copy(alpha = 0.15f) else Color.Transparent, label = "bg"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onOptionSelect(key) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) GoldenYellow else Surface3
                    ) {
                        Text(
                            text     = key,
                            style    = MaterialTheme.typography.labelLarge,
                            color    = if (isSelected) NavyBlueDark else OnSurfaceMid,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                }
            }
        }
    }
}

// ── Quiz Result Screen ────────────────────────────────────────────────────────

@Composable
fun QuizResultScreen(
    onContinue : () -> Unit,
    viewModel  : QuizViewModel = hiltViewModel()
) {
    val state  by viewModel.uiState.collectAsStateWithLifecycle()
    val result = state.result ?: return

    val passed      = result.passed
    val statusColor = if (passed) SuccessGreen else DangerRed
    val statusText  = if (passed) "उत्तीर्ण ✓" else "अनुत्तीर्ण ✗"

    Box(
        modifier         = Modifier.fillMaxSize().background(NavyBlueDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint               = statusColor,
                modifier           = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(statusText, style = MaterialTheme.typography.displayLarge, color = statusColor)
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "स्कोर: ${result.score}/${result.total}",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = if (passed) "बधाई! आपने प्रशिक्षण पूरा किया।"
                            else "न्यूनतम 70% आवश्यक है।",
                style     = MaterialTheme.typography.bodyMedium,
                color     = OnSurfaceMid,
                textAlign = TextAlign.Center
            )

            // Show remaining attempts if failed
            if (!passed && state.attemptsLeft > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = "शेष प्रयास: ${state.attemptsLeft}",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = WarningOrange,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            if (passed || state.attemptsLeft <= 0) {
                Button(
                    onClick  = onContinue,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GoldenYellow, contentColor = NavyBlueDark)
                ) {
                    Text("डैशबोर्ड पर जाएं", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                // Failed but attempts remain — offer retry
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick  = onContinue,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("बाद में") }

                    Button(
                        onClick  = { viewModel.retryQuiz() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GoldenYellow, contentColor = NavyBlueDark)
                    ) { Text("पुनः प्रयास") }
                }
            }
        }
    }
}

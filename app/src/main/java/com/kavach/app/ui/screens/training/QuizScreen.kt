package com.kavach.app.ui.screens.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.domain.model.QuizQuestion
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.OnSurface
import com.kavach.app.ui.theme.OnSurfaceMid
import com.kavach.app.ui.theme.Surface1
import com.kavach.app.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    trainingId: String,
    viewModel: QuizViewModel = hiltViewModel(),
    onSubmit: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.result) {
        val result = state.result
        if (result != null) {
            onSubmit(result.score)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MISSION VALIDATION QUIZ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { onSubmit(0) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlueDark,
                    titleContentColor = OnSurface,
                    navigationIconContentColor = OnSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyBlueDark)
                .padding(padding)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldenYellow)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (state.error != null) {
                        Text(
                            text = state.error ?: "",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.questions) { question ->
                            viewModel.onQuestionVisible(question.id)
                            QuestionItem(
                                question = question,
                                selectedOption = state.answers[question.id],
                                onSelect = { option -> viewModel.selectAnswer(question.id, option) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.submitQuiz() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !state.isSubmitting
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "SUBMIT VALIDATION QUIZ",
                                color = NavyBlueDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionItem(
    question: QuizQuestion,
    selectedOption: String?,
    onSelect: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question.question,
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OptionRow(label = "A. " + question.optionA, isSelected = selectedOption == "A", onClick = { onSelect("A") })
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(label = "B. " + question.optionB, isSelected = selectedOption == "B", onClick = { onSelect("B") })
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(label = "C. " + question.optionC, isSelected = selectedOption == "C", onClick = { onSelect("C") })
            Spacer(modifier = Modifier.height(8.dp))
            OptionRow(label = "D. " + question.optionD, isSelected = selectedOption == "D", onClick = { onSelect("D") })
        }
    }
}

@Composable
fun OptionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) GoldenYellow.copy(alpha = 0.2f) else Surface2,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = GoldenYellow, unselectedColor = OnSurfaceMid)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (isSelected) GoldenYellow else OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

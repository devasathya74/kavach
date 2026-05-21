package com.kavach.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TacticalKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (i in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (j in 0 until 3) {
                    val key = keys[i * 3 + j]
                    if (key.isNotEmpty()) {
                        KeyButton(
                            text = key,
                            onClick = { if (key == "⌫") onBackspace() else onDigit(key) }
                        )
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun KeyButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

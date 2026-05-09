package com.kavach.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "ACTIVE" -> Color(0xFF4CAF50)
        "SUSPENDED", "REVOKED", "LOCKED" -> Color(0xFFF44336)
        "PENDING", "UNDER_REVIEW" -> Color(0xFFFF9800)
        else -> Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.1f), 
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = status, 
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold, 
            color = color
        )
    }
}

@Composable
fun RoleBadge(role: String) {
    val color = when (role) {
        "COMMANDING_OFFICER", "ADMIN" -> Color(0xFFE91E63)
        "PILOT" -> Color(0xFF2196F3)
        "SUPERUSER" -> Color(0xFF4A148C)
        else -> Color(0xFF9C27B0)
    }
    Surface(
        color = color.copy(alpha = 0.1f), 
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = role.replace("_", " "), 
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold, 
            color = color
        )
    }
}

package com.kavach.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.Surface1

@Composable
fun DashboardCard(
    title: String,
    icon: ImageVector,
    color: Color,
    metric: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (enabled) color.copy(alpha = 0.05f) else Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .alpha(if (enabled) 1f else 0.5f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) color else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                    
                    if (metric != null && enabled) {
                        Surface(
                            color = color.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = metric,
                                style = MaterialTheme.typography.labelMedium,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (!enabled) {
                        Text(
                            text = "जल्द आ रहा है",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldenYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (enabled) Color.White else Color.Gray
                )
            }
        }
    }
}

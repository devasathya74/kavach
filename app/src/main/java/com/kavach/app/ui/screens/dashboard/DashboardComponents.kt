package com.kavach.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*
import com.kavach.app.util.ConnectionStatus

/**
 * Shared UI components for all three dashboard screens.
 * Extracted to avoid private-access errors across files.
 */

/** Connectivity status chip for TopAppBar actions. */
@Composable
fun ConnStatusChip(isOnline: Boolean) {
    Surface(
        color = (if (isOnline) SuccessGreen else DangerRed).copy(alpha = 0.13f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier.size(6.dp).clip(CircleShape)
                    .background(if (isOnline) SuccessGreen else DangerRed)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (isOnline) "ONLINE" else "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOnline) SuccessGreen else DangerRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Standard dashboard action tile used in all role dashboards. */
@Composable
fun DashTile(
    title  : String,
    icon   : ImageVector,
    color  : Color,
    active : Boolean = true,
    badge  : String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(112.dp).clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            if (active) color.copy(alpha = 0.07f) else Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null,
                        tint = if (active) color else Color.Gray,
                        modifier = Modifier.size(26.dp))
                    if (!active) {
                        Text("PENDING",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldenYellow.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp, letterSpacing = 1.sp)
                    } else if (badge != null && badge != "0") {
                        Surface(color = color.copy(alpha = 0.18f), shape = CircleShape) {
                            Text(badge, color = color, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold, lineHeight = 18.sp),
                    color = if (active) Color.White else Color.Gray)
            }
        }
    }
}

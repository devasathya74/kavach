package com.kavach.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    name: String,
    roleTitle: String,
    isOnline: Boolean,
    wsConnected: Boolean,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(GoldenYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(1).uppercase(),
                        color = NavyBlueDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "KAVACH",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = roleTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldenYellow.copy(alpha = 0.8f)
                    )
                }
            }
        },
        actions = {
            // WebSocket Status Indicator
            val wsColor = if (wsConnected) SuccessGreen else DangerRed
            
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(wsColor)
            )

            // Connection Chip (Passive)
            Surface(
                color = (if (isOnline) SuccessGreen else DangerRed).copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) SuccessGreen else DangerRed)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) SuccessGreen else DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Profile",
                    tint = GoldenYellow
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlueDark)
    )
}

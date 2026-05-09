package com.kavach.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.util.ConnectionStatus

@Composable
fun ConnectivityBanner(status: ConnectionStatus) {
    val isOffline = status != ConnectionStatus.AVAILABLE
    
    AnimatedVisibility(
        visible = isOffline,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val (backgroundColor, icon, text) = when (status) {
            ConnectionStatus.LOST, ConnectionStatus.UNAVAILABLE -> {
                Triple(Color(0xFFD32F2F), Icons.Default.CloudOff, "सिस्टम ऑफलाइन है (OFFLINE)")
            }
            ConnectionStatus.LOSING -> {
                Triple(Color(0xFFFFA000), Icons.Default.WifiOff, "कनेक्शन कमजोर है (WEAK SIGNAL)")
            }
            else -> Triple(Color.Transparent, Icons.Default.Wifi, "")
        }

        if (text.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

package com.kavach.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onGoToLive     : () -> Unit,
    onGoToOrders   : () -> Unit,
    onGoToProfile  : () -> Unit,
    pendingCount   : Int = 2,
    overdueCount   : Int = 1
) {
    Scaffold(
        containerColor = OfficialBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("आदेश / निर्देश", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("नमस्ते, जवान", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = onGoToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 1. FORCED ATTENTION MECHANISM (Unread Alert Banner)
            if (pendingCount > 0 || overdueCount > 0) {
                AlertBanner(pendingCount, overdueCount)
            }

            Text("प्राथमिक आदेश (Priority)", style = MaterialTheme.typography.titleMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)

            // 2 & 3. PRIORITY SYSTEM + STATUS CLARITY + TIME PRESSURE
            // High Priority / Overdue Order
            OrderCard(
                title = "सुरक्षा समीक्षा और अनुपालन",
                priority = "HIGH",
                status = "OVERDUE",
                timeRemaining = "समय सीमा समाप्त!",
                onClick = onGoToOrders
            )

            // Medium Priority / Pending Order
            OrderCard(
                title = "वीआईपी मूवमेंट ड्यूटी रोस्टर",
                priority = "MEDIUM",
                status = "PENDING",
                timeRemaining = "समय सीमा: 2 घंटे बाकी",
                onClick = onGoToOrders
            )

            // Low Priority / Acknowledged Order
            OrderCard(
                title = "साप्ताहिक रिपोर्ट सबमिशन",
                priority = "LOW",
                status = "ACKNOWLEDGED",
                timeRemaining = "पूर्ण",
                onClick = onGoToOrders
            )

            Spacer(Modifier.height(8.dp))
            Text("अन्य", style = MaterialTheme.typography.titleMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)

            // Live Broadcast (Command Channel)
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onGoToLive),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, tint = DangerRed)
                    Spacer(Modifier.width(16.dp))
                    Text("Live Broadcast (कमांड चैनल)", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AlertBanner(pending: Int, overdue: Int) {
    val bannerColor = if (overdue > 0) DangerRed else Color(0xFFFFB74D)
    val textColor = if (overdue > 0) Color.White else TextPrimary
    val message = if (overdue > 0) "⚠️ $overdue आदेश OVERDUE हैं! तुरंत जांचें।" else "⚠️ $pending आदेश लंबित हैं"
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bannerColor,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = "Alert", tint = textColor)
            Spacer(Modifier.width(12.dp))
            Text(message, color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OrderCard(title: String, priority: String, status: String, timeRemaining: String, onClick: () -> Unit) {
    val borderColor = when (priority) {
        "HIGH" -> DangerRed
        "MEDIUM" -> Color(0xFFFFB74D)
        else -> Color.LightGray
    }
    
    val statusColor = when (status) {
        "OVERDUE" -> DangerRed
        "PENDING" -> Color(0xFFFFB74D)
        "ACKNOWLEDGED" -> SuccessGreen
        else -> TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(timeRemaining, style = MaterialTheme.typography.bodyMedium, color = if(status == "OVERDUE") DangerRed else TextSecondary, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

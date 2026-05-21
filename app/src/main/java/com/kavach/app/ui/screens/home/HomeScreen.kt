package com.kavach.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.data.FakeDataProvider
import com.kavach.app.ui.data.FakeDataProvider.Alert
import com.kavach.app.ui.data.FakeDataProvider.Broadcast
import com.kavach.app.ui.components.ConnectivityBanner

/**
 * Home screen – tactical layout only using fake data.
 *
 * Layout order (strict, must not be changed):
 *   1. Connection banner
 *   2. Critical alert card (first item from ALERTS)
 *   3. Broadcast preview (first item from BROADCASTS)
 *   4. Quick actions grid (2x2 deterministic)
 *   5. Medium alerts list (remaining alerts)
 *
 * No business logic, ViewModel, or external dependencies – static rendering of
 * FakeDataProvider contents.
 */
@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 1. Connection Banner – solid color strip only
        ConnectivityBanner(status = "CONNECTED")
        Spacer(modifier = Modifier.height(12.dp))

        // 2. Critical Alert Card – full width, red background, max 2 lines title
        val criticalAlert = FakeDataProvider.ALERTS.firstOrNull()
        criticalAlert?.let { alert ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                backgroundColor = Color(0xFFB71C1C) // dark red
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                ) {
                    Text(
                        text = alert.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = alert.description,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* primary action – placeholder */ },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Acknowledge")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 3. Broadcast Preview – title, timestamp, short content
        val broadcast = FakeDataProvider.BROADCASTS.firstOrNull()
        broadcast?.let { b ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = b.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${b.timestamp}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = b.content,
                        maxLines = 2,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 4. Quick Actions Grid – deterministic 2x2
        val actions = FakeDataProvider.QUICK_ACTIONS
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (rowIndex in 0 until 2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (colIndex in 0 until 2) {
                        val actionIndex = rowIndex * 2 + colIndex
                        if (actionIndex < actions.size) {
                            val label = actions[actionIndex]
                            Button(
                                onClick = { /* placeholder */ },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                            ) {
                                Text(label)
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 5. Medium Alerts List – remaining alerts (skip first critical)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count = FakeDataProvider.ALERTS.size - 1) { idx ->
                val alert = FakeDataProvider.ALERTS[idx + 1]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = alert.title,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = alert.description,
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

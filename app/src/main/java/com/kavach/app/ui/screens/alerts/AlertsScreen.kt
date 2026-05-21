package com.kavach.app.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.kavach.app.ui.data.FakeDataProvider

/**
 * Alerts screen – simple vertical list.
 *
 * Order is deterministic:
 *   1. Critical alert (first element of FakeDataProvider.ALERTS)
 *   2. Remaining medium alerts.
 * No swipe, no expand, no filters.
 */
@Composable
fun AlertsScreen() {
    val alerts = FakeDataProvider.ALERTS
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (alerts.isNotEmpty()) {
            // Critical alert – styled differently
            item {
                val crit = alerts[0]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    // Dark red background for critical
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = crit.title,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = crit.description,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
            // Medium alerts
            items(alerts.drop(1)) { alert ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = alert.title,
                            fontWeight = FontWeight.Medium
                        )
                        Text(text = alert.description)
                    }
                }
            }
        }
    }
}

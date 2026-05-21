package com.kavach.app.ui.screens.broadcast
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
import androidx.compose.ui.unit.sp


/**
 * Broadcast screen – compact cards showing title, timestamp and short preview.
 * No media, images, carousels or animations.
 */
@Composable
fun BroadcastScreen() {
    val broadcasts = FakeDataProvider.BROADCASTS
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(broadcasts) { broadcast ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = broadcast.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${broadcast.timestamp}",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = broadcast.content,
                        maxLines = 2,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}


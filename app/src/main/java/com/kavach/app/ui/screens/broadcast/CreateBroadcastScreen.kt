package com.kavach.app.ui.screens.broadcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.components.FilterChipGroup
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.OnSurface
import com.kavach.app.ui.theme.OnSurfaceMid
import com.kavach.app.ui.theme.Surface1
import com.kavach.app.ui.theme.DangerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBroadcastScreen(
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BROADCAST DISPATCHER", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlueDark,
                    titleContentColor = OnSurface,
                    navigationIconContentColor = OnSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyBlueDark)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "DISPATCH NEW MISSION DIRECTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldenYellow,
                        letterSpacing = 1.sp
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.onTitleChange(it) },
                        label = { Text("Title / Directive Subject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurfaceMid,
                            focusedBorderColor = GoldenYellow,
                            unfocusedBorderColor = Surface1,
                            focusedLabelColor = GoldenYellow,
                            unfocusedLabelColor = OnSurfaceMid
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.content,
                        onValueChange = { viewModel.onContentChange(it) },
                        label = { Text("Broadcast Message Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurfaceMid,
                            focusedBorderColor = GoldenYellow,
                            unfocusedBorderColor = Surface1,
                            focusedLabelColor = GoldenYellow,
                            unfocusedLabelColor = OnSurfaceMid
                        )
                    )
                }

                item {
                    Text(
                        text = "PRIORITY LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceMid
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val priorities = listOf("INFO", "NORMAL", "HIGH")
                    FilterChipGroup(
                        options = priorities,
                        selectedOption = state.priority,
                        onOptionSelected = { priority ->
                            if (priority != null) {
                                viewModel.onPriorityChange(priority)
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.imageUrl ?: "",
                        onValueChange = { viewModel.onImageUrlChange(it.ifBlank { null }) },
                        label = { Text("Attachment Image URL (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurfaceMid,
                            focusedBorderColor = GoldenYellow,
                            unfocusedBorderColor = Surface1,
                            focusedLabelColor = GoldenYellow,
                            unfocusedLabelColor = OnSurfaceMid
                        )
                    )
                }

                item {
                    Text(
                        text = "RECIPIENTS (LEAVE EMPTY TO BROADCAST TO ALL)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceMid
                    )
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GoldenYellow)
                        }
                    }
                } else if (state.officers.isEmpty()) {
                    item {
                        Text(
                            text = "No active personnel found to select.",
                            color = OnSurfaceMid,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(state.officers) { officer ->
                        val isSelected = state.selectedOfficerIds.contains(officer.id)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Surface1 else Surface1.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleOfficerSelection(officer.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isSelected) GoldenYellow else OnSurfaceMid,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = officer.profile?.name?.uppercase(java.util.Locale.getDefault()) ?: "UNKNOWN",
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "PNO: ${officer.pno} | ${officer.role}",
                                            color = OnSurfaceMid,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = GoldenYellow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Sticky Dispatch Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(NavyBlueDark)
                    .padding(16.dp)
            ) {
                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(bottom = 48.dp)
                    )
                }

                Button(
                    onClick = { viewModel.sendBroadcast() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                    enabled = !state.isSending
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = NavyBlueDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DISPATCH DIRECTIVE",
                            color = NavyBlueDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

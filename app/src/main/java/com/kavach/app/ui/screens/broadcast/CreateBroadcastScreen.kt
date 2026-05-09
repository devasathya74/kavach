package com.kavach.app.ui.screens.broadcast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBroadcastScreen(
    onBack: () -> Unit,
    viewModel: CreateBroadcastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showOfficerPicker by remember { mutableStateOf(false) }

    if (uiState.success) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("नया ब्रॉडकास्ट (New Broadcast)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlueDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("Command Order Details", color = GoldenYellow, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.onTitleChange(it) },
                    label = { Text("शीर्षक (Title)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.content,
                    onValueChange = { viewModel.onContentChange(it) },
                    label = { Text("संदेश (Message / Order)") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Text("Priority Level", color = GoldenYellow, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityChip("INFO", "Info", GoldenYellow, uiState.priority == "INFO") { viewModel.onPriorityChange("INFO") }
                    PriorityChip("URGENT", "Urgent", Color(0xFFF59E0B), uiState.priority == "URGENT") { viewModel.onPriorityChange("URGENT") }
                    PriorityChip("CRITICAL", "Critical", DangerRed, uiState.priority == "CRITICAL") { viewModel.onPriorityChange("CRITICAL") }
                }
            }

            item {
                Text("Targeting (Command Scope)", color = GoldenYellow, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("अधिकारी चयन (Target Officers)", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (uiState.selectedOfficerIds.isEmpty()) "सभी को भेजें (Broadcast to All)" else "${uiState.selectedOfficerIds.size} अधिकारी चयनित",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = { showOfficerPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Surface3),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("बदलें", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text("Media Attachment", color = GoldenYellow, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.imageUrl ?: "",
                    onValueChange = { viewModel.onImageUrlChange(it.ifBlank { null }) },
                    label = { Text("Image URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldenYellow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.4f)) }
                )
            }

            item {
                Spacer(Modifier.height(20.dp))
                if (uiState.error != null) {
                    Text(uiState.error!!, color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                
                Button(
                    onClick = { viewModel.sendBroadcast() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isSending
                ) {
                    if (uiState.isSending) {
                        CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = NavyBlueDark)
                        Spacer(Modifier.width(8.dp))
                        Text("ब्रॉडकास्ट भेजें (Transmit Command)", color = NavyBlueDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showOfficerPicker) {
        OfficerPickerDialog(
            officers = uiState.officers,
            selectedIds = uiState.selectedOfficerIds,
            onToggle = { viewModel.toggleOfficerSelection(it) },
            onDismiss = { showOfficerPicker = false }
        )
    }
}

@Composable
fun PriorityChip(
    id: String,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(40.dp),
        color = if (isSelected) color else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (isSelected) NavyBlueDark else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun OfficerPickerDialog(
    officers: List<com.kavach.app.data.remote.dto.personnel.OfficerDto>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = { Text("अधिकारी चुनें (Target Officers)", color = Color.White) },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(officers) { officer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(officer.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(officer.id),
                            onCheckedChange = { onToggle(officer.id) },
                            colors = CheckboxDefaults.colors(checkedColor = GoldenYellow)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(officer.profile?.name ?: officer.pno, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${officer.profile?.rank?.name ?: ""} | ${officer.pno}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ठीक है (Done)", color = GoldenYellow)
            }
        }
    )
}

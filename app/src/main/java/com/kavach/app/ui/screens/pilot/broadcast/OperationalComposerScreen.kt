package com.kavach.app.ui.screens.pilot.broadcast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.local.entity.OfficerWithProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalComposerScreen(
    onNavigateBack: () -> Unit,
    composerViewModel: OperationalComposerViewModel = hiltViewModel(),
    selectionViewModel: SelectionEngineViewModel = hiltViewModel()
) {
    val title by composerViewModel.title.collectAsState()
    val content by composerViewModel.content.collectAsState()
    val priority by composerViewModel.priority.collectAsState()

    val visiblePersonnel by selectionViewModel.visiblePersonnel.collectAsState()
    val selectedUserIds by selectionViewModel.selectedUserIds.collectAsState()
    val searchQuery by selectionViewModel.searchQuery.collectAsState()

    var showPreviewDialog by remember { mutableStateOf(false) }
    var isDispatching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operational Dispatch") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            StickyPreviewFooter(
                selectedCount = selectedUserIds.size,
                priority = priority,
                onPreviewClick = { showPreviewDialog = true },
                enabled = title.isNotBlank() && selectedUserIds.isNotEmpty() && !isDispatching
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. Metadata Section
            item {
                Text("Broadcast Metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { composerViewModel.updateTitle(it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { composerViewModel.updateContent(it) },
                    label = { Text("Content (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. Attachments (Placeholder for actual Picker logic)
            item {
                Text("Attachments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Upload constraints: 5 Images, 1 PDF (Max 15MB)", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { /* Launch File Picker */ }, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Attach Media")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Selection Engine (Filters & Live Search)
            item {
                Text("Target Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { selectionViewModel.onSearchQueryChanged(it) },
                    label = { Text("Search Personnel (Name/PNO)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Simulating dropdown/filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { selectionViewModel.onUnitSelected("UNIT_1") },
                        label = { Text("Unit: ALL") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { selectionViewModel.onCompanySelected("COMP_A") },
                        label = { Text("Company: Alpha") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Visible Results
            if (visiblePersonnel.isNotEmpty()) {
                item {
                    Text("Search Results", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Limiting rendering in UI for huge lists (though LazyColumn handles it, we can cap)
                val displayList = visiblePersonnel.take(50)
                items(displayList) { officer ->
                    val isSelected = selectedUserIds.contains(officer.officer.id)
                    PersonnelRow(
                        officer = officer,
                        isSelected = isSelected,
                        onToggle = { selectionViewModel.toggleSelection(officer.officer.id) }
                    )
                }
                if (visiblePersonnel.size > 50) {
                    item {
                        Text("... and ${visiblePersonnel.size - 50} more. Refine search.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (searchQuery.isNotEmpty()) {
                item {
                    Text("No personnel found.")
                }
            }
        }
    }

    if (showPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = { Text("Confirm Dispatch") },
            text = {
                Column {
                    Text("You are about to queue this broadcast.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recipients: ${selectedUserIds.size}", fontWeight = FontWeight.Bold)
                    Text("Priority: $priority", fontWeight = FontWeight.Bold)
                    Text("Mode: Transactional Queue", color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPreviewDialog = false
                    isDispatching = true
                    composerViewModel.enqueueDispatch(
                        selectedUserIds = selectedUserIds,
                        onSuccess = { onNavigateBack() },
                        onError = { isDispatching = false }
                    )
                }) {
                    Text("Queue Dispatch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PersonnelRow(
    officer: OfficerWithProfile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(officer.profile?.name ?: "Unknown", fontWeight = FontWeight.Medium)
            Text(officer.officer.pno, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun StickyPreviewFooter(
    selectedCount: Int,
    priority: String,
    onPreviewClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Selected: $selectedCount", fontWeight = FontWeight.Bold)
                Text("Priority: $priority", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onPreviewClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Preview Dispatch")
            }
        }
    }
}

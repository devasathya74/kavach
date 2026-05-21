package com.kavach.app.ui.screens.pilot.personnel

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.components.RoleBadge
import com.kavach.app.ui.components.StatusBadge
import com.kavach.app.ui.components.FilterChipGroup
import com.kavach.app.data.local.entity.BulkMutationEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelListScreen(
    viewModel: PersonnelListViewModel = hiltViewModel(),
    onUserClick: (String) -> Unit,
    onAddUser: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    // Derived Selection State to avoid recomposition flood
    val isSelectionMode = state.selectionMode
    val selectedCount = state.selectedIds.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SelectionAwareTopBar(
                selectionMode = isSelectionMode,
                selectedCount = selectedCount,
                onCloseSelection = { viewModel.toggleSelectionMode(false) },
                onRefresh = { viewModel.syncUsers(isRefresh = true) },
                onDeleteSelected = { showBulkDialog = "DELETE" },
                onBlockSelected = { showBulkDialog = "BLOCK" }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = onAddUser,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add User")
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                SelectionSummaryBar(count = selectedCount)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            MutationStatusBanner(queue = state.bulkMutationQueue)
            
            SearchBar(
                query = state.query.query,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onFilterClick = { showFilterSheet = true }
            )

            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val uiState = state.uiState) {
                    is PersonnelUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is PersonnelUiState.Error -> {
                        ErrorState(message = uiState.message, onRetry = { viewModel.syncUsers(isRefresh = true) })
                    }
                    is PersonnelUiState.Success -> {
                        if (uiState.users.isEmpty()) {
                            EmptyState()
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = uiState.users,
                                    key = { it.id },
                                    contentType = { "personnel" }
                                ) { user ->
                                    val isSelected = state.selectedIds.contains(user.id)
                                    OfficerCard(
                                        user = user,
                                        isSelected = isSelected,
                                        selectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleUserSelection(user.id)
                                            } else {
                                                onUserClick(user.id)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                viewModel.toggleSelectionMode(true)
                                                viewModel.toggleUserSelection(user.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    if (showBulkDialog != null) {
        BulkActionConfirmationDialog(
            actionType = showBulkDialog!!,
            count = selectedCount,
            onConfirm = {
                viewModel.bulkAction(showBulkDialog!!)
                showBulkDialog = null
            },
            onDismiss = { showBulkDialog = null }
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FilterContent(
                queryState = state.query,
                onUnitChange = { viewModel.onUnitFilterChange(it) },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionAwareTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    onCloseSelection: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBlockSelected: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            if (selectionMode) {
                Text("$selectedCount SELECTED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Text("PERSONNEL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        navigationIcon = {
            if (selectionMode) {
                IconButton(onClick = onCloseSelection) {
                    Icon(Icons.Default.Close, null)
                }
            }
        },
        actions = {
            if (selectionMode) {
                IconButton(onClick = onBlockSelected) {
                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null)
                }
            }
        }
    )
}

@Composable
fun SelectionSummaryBar(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("$count PERSONNEL SELECTED ACROSS RESULTS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MutationStatusBanner(queue: List<BulkMutationEntity>) {
    val activeMutation = queue.firstOrNull { it.status == "PROCESSING" || it.status == "QUEUED" } ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeMutation.status == "PROCESSING") {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Bulk ${activeMutation.actionType} in progress...",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OfficerCard(
    user: PersonnelListItemUiModel,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(
                        Brush.linearGradient(colors = listOf(Color(0xFF6200EE), Color(0xFF03DAC6)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${user.rank} | ${user.pno}", style = MaterialTheme.typography.bodySmall)
                Row {
                    StatusBadge(user.status)
                    Spacer(Modifier.width(8.dp))
                    RoleBadge(user.role)
                }
            }

            Text(user.unitCode, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun BulkActionConfirmationDialog(
    actionType: String,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CONFIRM BULK $actionType", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("YOU HAVE SELECTED $count PERSONNEL.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "This action will be enqueued as a governed bulk mutation and processed in chunks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("PROCEED")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

// ... Rest of the components (SearchBar, FilterContent, etc.) stay similar or slightly updated
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onFilterClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search PNO or Name...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onFilterClick) {
            Icon(Icons.Default.FilterList, null)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Error: $message")
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No results found")
    }
}

@Composable
fun FilterContent(queryState: PersonnelQueryState, onUnitChange: (String?) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text("Filter by Unit", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        FilterChipGroup(
            options = listOf("HQ", "RTC", "BATTALION"),
            selectedOption = queryState.unitType,
            onOptionSelected = onUnitChange
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Apply")
        }
    }
}

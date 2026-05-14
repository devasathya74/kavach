package com.kavach.app.ui.screens.pilot.personnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.components.RoleBadge
import com.kavach.app.ui.components.StatusBadge
import com.kavach.app.ui.components.FilterChipGroup
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
    var showFilterSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    // Detect when user scrolls to bottom
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            // Only trigger if we have items (initial load is handled separately) 
            // and we haven't reached the end or currently loading.
            totalItemsCount > 0 && 
            lastVisibleItemIndex >= (totalItemsCount - 5) && 
            !state.isLoading && 
            !state.isRefreshing &&
            !state.endOfPaginationReached
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncUsers(isRefresh = true)
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.syncUsers()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "PERSONNEL MANAGEMENT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.onRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddUser,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add User")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            SearchBar(
                query = state.query.query,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onFilterClick = { showFilterSheet = true }
            )

            if (state.error != null) {
                ErrorState(message = state.error!!, onRetry = { viewModel.onRefresh() })
            } else if (state.users.isEmpty() && !state.isLoading && !state.isRefreshing) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.users) { user ->
                        OfficerCard(user = user, onClick = { onUserClick(user.id) })
                    }
                    
                    if (state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                FilterContent(
                    queryState = state.query,
                    onUnitChange = { viewModel.onUnitFilterChange(it) },
                    onCompanyChange = { viewModel.onCompanyFilterChange(it) },
                    onPlatoonChange = { viewModel.onPlatoonFilterChange(it) },
                    onDismiss = { showFilterSheet = false }
                )
            }
        }
        }
}

// CreateUserDialog is removed in favor of UserRegistrationScreen

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No personnel found.", style = MaterialTheme.typography.bodyLarge)
        Text("Try adjusting your search or filters.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠️ Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search PNO or Name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(Modifier.width(8.dp))
        
        IconButton(
            onClick = onFilterClick,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(Icons.Default.FilterList, contentDescription = "Filter")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerCard(
    user: PersonnelListItemUiModel,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(user.status)
                    Spacer(Modifier.width(8.dp))
                    RoleBadge(user.role)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(user.unitCode, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(user.company, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun FilterContent(
    queryState: PersonnelQueryState,
    onUnitChange: (String?) -> Unit,
    onCompanyChange: (String?) -> Unit,
    onPlatoonChange: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Filter Personnel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = {
                    onUnitChange(null)
                    onCompanyChange(null)
                    onPlatoonChange(null)
                }
            ) {
                Text("Clear All", color = MaterialTheme.colorScheme.error)
            }
        }
        
        Spacer(Modifier.height(24.dp))

        // 1. Unit Type Filter
        Text("Select Unit Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        FilterChipGroup(
            options = listOf("HQ", "RTC", "BATTALION"),
            displayNames = listOf("Headquarters", "RTC", "Company Units"),
            selectedOption = queryState.unitType,
            onOptionSelected = onUnitChange
        )

        // 2. Company Filter (Only visible if Battalion is selected)
        if (queryState.unitType == "BATTALION") {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(24.dp))
            
            Text("Select Company", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            FilterChipGroup(
                options = listOf("A", "B", "C", "D", "E", "F", "G", "H"),
                selectedOption = queryState.company,
                onOptionSelected = onCompanyChange
            )
        }

        // 3. Platoon Filter (Only visible if Company is selected)
        if (queryState.unitType == "BATTALION" && queryState.company != null) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(24.dp))
            
            Text("Select Platoon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            FilterChipGroup(
                options = listOf("1", "2", "3"),
                selectedOption = queryState.platoon?.toString(),
                onOptionSelected = { onPlatoonChange(it?.toIntOrNull()) }
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Apply Filters", fontWeight = FontWeight.Bold)
        }
    }
}

// FilterChipGroup is moved to shared components

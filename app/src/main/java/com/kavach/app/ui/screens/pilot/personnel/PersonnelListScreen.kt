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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelListScreen(
    viewModel: PersonnelListViewModel = hiltViewModel(),
    onUserClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showCreateUserDialog by remember { mutableStateOf(false) }

    // Detect when user scrolls to bottom
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= (totalItemsCount - 5) && !state.isLoading && !state.endOfPaginationReached
        }
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
                onClick = { showCreateUserDialog = true },
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
        if (showCreateUserDialog) {
            CreateUserDialog(
                onDismiss = { showCreateUserDialog = false },
                onConfirm = { userData ->
                    viewModel.createUser(userData)
                    showCreateUserDialog = false
                }
            )
        }
    }
}

fun validatePassword(password: String): String? {
    if (password.length < 8) return "Minimum 8 characters required"
    if (!password.any { it.isUpperCase() }) return "1 capital letter required"
    if (!password.any { it.isLowerCase() }) return "1 small letter required"
    if (password.count { it.isDigit() } < 2) return "Minimum 2 digits required"
    val specialRegex = Regex("[@$!%*?&#]")
    if (!specialRegex.containsMatchIn(password)) return "1 special character required"
    return null
}

@Composable
fun CreateUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Any>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pno by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf("USER") }
    var rankCode by remember { mutableStateOf("CONST") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Personnel", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pno, onValueChange = { pno = it }, label = { Text("PNO") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password, 
                    onValueChange = { 
                        password = it 
                        passwordError = null
                    }, 
                    label = { Text("Initial Password") }, 
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError != null,
                    supportingText = { passwordError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    )
                )
                
                Text("Role", style = MaterialTheme.typography.labelMedium)
                FilterChipGroup(
                    options = listOf("USER", "PILOT", "COMMANDING_OFFICER"),
                    selectedOption = role,
                    onOptionSelected = { it?.let { role = it } }
                )
                
                Text("Rank", style = MaterialTheme.typography.labelMedium)
                FilterChipGroup(
                    options = listOf("CONST", "ASI", "SI", "INSP", "DSP", "SP"),
                    selectedOption = rankCode,
                    onOptionSelected = { it?.let { rankCode = it } }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val error = validatePassword(password)
                if (error != null) {
                    passwordError = error
                    return@Button
                }

                val data = mapOf(
                    "pno" to pno,
                    "role" to role,
                    "password" to password,
                    "profile" to mapOf(
                        "name" to name,
                        "rank_code" to rankCode
                    )
                )
                onConfirm(data)
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterChipGroup(
    options: List<String>,
    displayNames: List<String>? = null,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val label = displayNames?.getOrNull(index) ?: option
            FilterChip(
                selected = selectedOption == option,
                onClick = {
                    if (selectedOption == option) onOptionSelected(null)
                    else onOptionSelected(option)
                },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

package com.kavach.app.ui.screens.broadcast

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kavach.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBroadcastScreen(
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate back when queued successfully
    LaunchedEffect(state.dispatchState) {
        if (state.dispatchState is DispatchState.Queued) {
            snackbarHostState.showSnackbar("Broadcast queued for dispatch")
            onBack()
        }
    }

    // Show error in snackbar
    LaunchedEffect(state.dispatchState) {
        if (state.dispatchState is DispatchState.Failed) {
            snackbarHostState.showSnackbar(
                (state.dispatchState as DispatchState.Failed).reason
            )
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.onImagePicked(uri, mimeType)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OPERATIONAL ORDER DISPATCH",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CONSOLE",
                            fontSize = 10.sp,
                            color = GoldenYellow,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
            ) {

                // ── SECTION LABEL ─────────────────────────────────────────
                item {
                    Text(
                        text = "DISPATCH NEW MISSION DIRECTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldenYellow,
                        letterSpacing = 1.5.sp
                    )
                }

                // ── 1. BROADCAST TITLE ────────────────────────────────────
                item {
                    SectionLabel("1. BROADCAST TITLE")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("e.g. Battalion Movement Order") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = kavachTextFieldColors()
                    )
                }

                // ── 2. MESSAGE CONTENT ────────────────────────────────────
                item {
                    SectionLabel("2. BROADCAST MESSAGE")
                    Text(
                        text = "(Optional if order image is uploaded)",
                        color = OnSurfaceLow,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = state.content,
                        onValueChange = viewModel::onContentChange,
                        label = { Text("Broadcast Message Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        colors = kavachTextFieldColors()
                    )
                }

                // ── 3. ORDER IMAGE UPLOAD ─────────────────────────────────
                item {
                    SectionLabel("3. UPLOAD ORDER IMAGE")
                    Spacer(Modifier.height(6.dp))
                    OrderImageUploadBox(
                        attachmentState = state.attachmentState,
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        onRemove = viewModel::onRemoveAttachment
                    )
                }

                // ── 4. UNIT FILTER ────────────────────────────────────────
                item {
                    SectionLabel("4. FILTER BY UNIT")
                    Spacer(Modifier.height(6.dp))
                    UnitDropdown(
                        units = state.availableUnits.map { it.unitName to it.unitCode },
                        selectedCode = state.selectedUnit,
                        onSelect = viewModel::onUnitSelected
                    )
                }

                // ── 5. COMPANY FILTER (appears after unit selected) ───────
                item {
                    AnimatedVisibility(
                        visible = state.selectedUnit != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            SectionLabel("5. FILTER BY COMPANY")
                            Spacer(Modifier.height(6.dp))
                            CompanyDropdown(
                                companies = state.availableCompanies,
                                selectedCompany = state.selectedCompany,
                                onSelect = viewModel::onCompanySelected
                            )
                        }
                    }
                }

                // ── 6. SEARCH PERSONNEL ───────────────────────────────────
                item {
                    SectionLabel("6. SEARCH PERSONNEL")
                    Text(
                        text = "Search by PNO or Name — debounced DB query",
                        color = OnSurfaceLow,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Search PNO / Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceMid)
                        },
                        trailingIcon = {
                            if (state.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = GoldenYellow,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = kavachTextFieldColors()
                    )
                }

                // ── 7. PERSONNEL LIST ─────────────────────────────────────
                if (state.selectedUserIds.isNotEmpty()) {
                    item {
                        SelectionSummaryChip(count = state.selectedUserIds.size)
                    }
                }

                items(
                    items = state.searchResults,
                    key = { officer -> officer.officer.id }
                ) { officer ->
                    // derivedStateOf prevents full list recomposition on each toggle
                    val isSelected by remember {
                        derivedStateOf { state.selectedUserIds.contains(officer.officer.id) }
                    }
                    OfficerSelectCard(
                        officer = officer,
                        isSelected = isSelected,
                        onToggle = { viewModel.toggleSelection(officer.officer.id) }
                    )
                }

                if (state.searchResults.isEmpty() && (state.selectedUnit != null || state.searchQuery.isNotBlank())) {
                    item {
                        Text(
                            text = "No personnel found. Adjust filters or search query.",
                            color = OnSurfaceLow,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                // ── 8. DELIVERY MODE ──────────────────────────────────────
                item {
                    SectionLabel("8. DELIVERY MODE")
                    Spacer(Modifier.height(6.dp))
                    DeliveryModePanel(
                        requireAck = state.requireAck,
                        isHighPriority = state.isHighPriority,
                        isEmergency = state.isEmergency,
                        onRequireAckChange = viewModel::onRequireAckChange,
                        onHighPriorityChange = viewModel::onHighPriorityChange,
                        onEmergencyChange = viewModel::onEmergencyChange
                    )
                }

                // Validation error
                if (state.validationError != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DangerRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.validationError!!,
                                color = DangerRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── STICKY DISPATCH BUTTON ────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(NavyBlueDark.copy(alpha = 0.97f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val isBusy = state.dispatchState is DispatchState.Saving
                Button(
                    onClick = { if (!isBusy) viewModel.dispatchBroadcast() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isEmergency) DangerRed else GoldenYellow,
                        disabledContainerColor = Surface3
                    ),
                    enabled = !isBusy
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(color = NavyBlueDark, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    } else {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = NavyBlueDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isEmergency) "EMERGENCY DISPATCH" else "DISPATCH BROADCAST",
                            color = NavyBlueDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Reusable Sub-Composables ──────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = OnSurfaceMid,
        letterSpacing = 1.sp
    )
}

@Composable
private fun kavachTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurfaceMid,
    focusedBorderColor = GoldenYellow,
    unfocusedBorderColor = Surface3,
    focusedLabelColor = GoldenYellow,
    unfocusedLabelColor = OnSurfaceMid,
    cursorColor = GoldenYellow
)

@Composable
private fun OrderImageUploadBox(
    attachmentState: AttachmentState,
    onPickImage: () -> Unit,
    onRemove: () -> Unit
) {
    when (attachmentState) {
        is AttachmentState.None -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .border(1.dp, Surface3, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickImage),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = OnSurfaceLow, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to upload order image", color = OnSurfaceLow, fontSize = 13.sp)
                    Text("JPG • PNG supported", color = OnSurfaceLow, fontSize = 11.sp)
                }
            }
        }
        is AttachmentState.Selected -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GoldenYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = File(attachmentState.localPath),
                    contentDescription = "Order image preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Remove button overlay
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                // Label
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("ORDER IMAGE — READY TO UPLOAD", color = GoldenYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        is AttachmentState.Failed -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DangerRed.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, DangerRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Image load failed", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(attachmentState.reason, color = OnSurfaceLow, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onPickImage) { Text("Retry", color = GoldenYellow, fontSize = 12.sp) }
                }
            }
        }
        else -> { /* Uploading / Ready handled by WorkManager — not shown here */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    units: List<Pair<String, String>>, // name to code
    selectedCode: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = units.find { it.second == selectedCode }?.first ?: "Select Unit"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text("Unit") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = OnSurfaceMid) },
            colors = kavachTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Surface2)
        ) {
            DropdownMenuItem(
                text = { Text("All Units", color = OnSurfaceLow, fontSize = 13.sp) },
                onClick = { onSelect(null); expanded = false }
            )
            units.forEach { (name, code) ->
                DropdownMenuItem(
                    text = { Text(name, color = OnSurface, fontSize = 13.sp) },
                    onClick = { onSelect(code); expanded = false },
                    leadingIcon = {
                        if (code == selectedCode) Icon(Icons.Default.Check, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyDropdown(
    companies: List<String>,
    selectedCompany: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedCompany ?: "Select Company",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text("Company") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = OnSurfaceMid) },
            colors = kavachTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Surface2)
        ) {
            DropdownMenuItem(
                text = { Text("All Companies", color = OnSurfaceLow, fontSize = 13.sp) },
                onClick = { onSelect(null); expanded = false }
            )
            companies.forEach { company ->
                DropdownMenuItem(
                    text = { Text(company, color = OnSurface, fontSize = 13.sp) },
                    onClick = { onSelect(company); expanded = false },
                    leadingIcon = {
                        if (company == selectedCompany) Icon(Icons.Default.Check, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionSummaryChip(count: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = GoldenYellow.copy(alpha = 0.15f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "✓  $count personnel selected",
            color = GoldenYellow,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OfficerSelectCard(
    officer: com.kavach.app.data.local.entity.OfficerWithProfile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val bgColor = if (isSelected) Surface2 else Surface1.copy(alpha = 0.45f)
    val borderColor = if (isSelected) GoldenYellow.copy(alpha = 0.6f) else Color.Transparent

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isSelected) GoldenYellow else OnSurfaceMid,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = officer.profile?.name?.uppercase(java.util.Locale.getDefault()) ?: officer.officer.pno,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "PNO: ${officer.officer.pno}  |  ${officer.profile?.companyName ?: officer.officer.unitName}",
                        color = OnSurfaceMid,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = GoldenYellow, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DeliveryModePanel(
    requireAck: Boolean,
    isHighPriority: Boolean,
    isEmergency: Boolean,
    onRequireAckChange: (Boolean) -> Unit,
    onHighPriorityChange: (Boolean) -> Unit,
    onEmergencyChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface2, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DeliveryModeRow(
            label = "Require ACK",
            sublabel = "Recipients must acknowledge receipt",
            checked = requireAck,
            onToggle = onRequireAckChange,
            activeColor = GoldenYellow
        )
        Divider(color = Divider.copy(alpha = 0.4f))
        DeliveryModeRow(
            label = "High Priority",
            sublabel = "Escalated delivery — FCM high priority",
            checked = isHighPriority || isEmergency,
            onToggle = onHighPriorityChange,
            activeColor = ColorWarning
        )
        Divider(color = Divider.copy(alpha = 0.4f))
        DeliveryModeRow(
            label = "EMERGENCY",
            sublabel = "Sets CRITICAL priority — backend escalation",
            checked = isEmergency,
            onToggle = onEmergencyChange,
            activeColor = DangerRed
        )
    }
}

@Composable
private fun DeliveryModeRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    activeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (checked) activeColor else OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = sublabel,
                color = OnSurfaceLow,
                fontSize = 10.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = activeColor,
                checkedTrackColor = activeColor.copy(alpha = 0.3f),
                uncheckedThumbColor = OnSurfaceLow,
                uncheckedTrackColor = Surface3
            )
        )
    }
}

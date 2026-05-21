package com.kavach.app.ui.screens.broadcast

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.kavach.app.data.local.dao.UnitSummary
import com.kavach.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBroadcastScreen(
    viewModel: CreateBroadcastViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Navigate away once broadcast is queued or sent
    LaunchedEffect(state.dispatchState) {
        if (state.dispatchState == DispatchState.QUEUED ||
            state.dispatchState == DispatchState.SENT) {
            onBack()
        }
    }

    // Image picker launcher
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Determine MIME type from content resolver
            val context = android.app.Application()
        }
    }
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.onImageSelected(uri, mimeType)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "OPERATIONAL ORDER DISPATCH",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "CONSOLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldenYellow,
                            letterSpacing = 2.sp
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
                    navigationIconContentColor = GoldenYellow
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
            ) {

                // ── Section Header ────────────────────────────────────
                item {
                    SectionLabel(
                        icon = Icons.Default.Campaign,
                        label = "DISPATCH NEW MISSION DIRECTIVE",
                        tint = GoldenYellow
                    )
                }

                // ── 1. TITLE BOX ──────────────────────────────────────
                item {
                    OperationalTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = "Broadcast Title",
                        placeholder = "e.g. Battalion Movement Order, Emergency Deployment",
                        icon = Icons.Default.Title,
                        singleLine = true,
                        imeAction = ImeAction.Next
                    )
                }

                // ── 2. MESSAGE CONTENT BOX ────────────────────────────
                item {
                    OperationalTextField(
                        value = state.content,
                        onValueChange = viewModel::onContentChange,
                        label = "Broadcast Message" + if (state.attachmentLocalPath != null) " (Optional)" else " *",
                        placeholder = "Enter operational directive, instructions, or notice...",
                        icon = Icons.Default.Message,
                        minLines = 4,
                        maxLines = 8
                    )
                }

                // ── 3. IMAGE / ORDER UPLOAD BOX ───────────────────────
                item {
                    SectionLabel(
                        icon = Icons.Default.Image,
                        label = "ORDER IMAGE / ATTACHMENT",
                        tint = OnSurfaceMid
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    AnimatedContent(
                        targetState = state.attachmentLocalPath != null,
                        label = "attachment_state"
                    ) { hasAttachment ->
                        if (hasAttachment) {
                            // Preview card
                            OrderImagePreview(
                                localPath = state.attachmentLocalPath!!,
                                isUploading = state.dispatchState == DispatchState.UPLOADING,
                                onRemove = viewModel::onImageRemoved
                            )
                        } else {
                            // Upload prompt
                            UploadPromptBox(
                                isLoading = state.isImageCopying,
                                onClick = { imagePickerLauncher.launch("image/*") }
                            )
                        }
                    }
                }

                // ── 4. TARGET FILTER SYSTEM ───────────────────────────
                item {
                    SectionLabel(
                        icon = Icons.Default.FilterAlt,
                        label = "TARGET FILTER",
                        tint = OnSurfaceMid
                    )
                }

                // Level 1 — Unit Dropdown
                item {
                    UnitDropdown(
                        label = "LEVEL 1 — UNIT",
                        options = state.availableUnits,
                        selected = state.selectedUnit,
                        onSelect = viewModel::onUnitSelected
                    )
                }

                // Level 2 — Company Dropdown (only when unit selected)
                if (state.selectedUnit != null) {
                    item {
                        AnimatedVisibility(visible = state.availableCompanies.isNotEmpty()) {
                            CompanyDropdown(
                                label = "LEVEL 2 — COMPANY",
                                options = state.availableCompanies,
                                selected = state.selectedCompany,
                                onSelect = viewModel::onCompanySelected
                            )
                        }
                    }
                }

                // ── 5. LIVE USER SEARCH ───────────────────────────────
                item {
                    SectionLabel(
                        icon = Icons.Default.Search,
                        label = "SEARCH PERSONNEL",
                        tint = OnSurfaceMid
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Search by PNO or Name") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = GoldenYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = OnSurfaceMid,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = operationalTextFieldColors()
                    )
                }

                // ── 6. PERSONNEL LIST ─────────────────────────────────
                if (state.selectedOfficerIds.isNotEmpty()) {
                    item {
                        Text(
                            "${state.selectedOfficerIds.size} SELECTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldenYellow,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (state.isPersonnelLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GoldenYellow, modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (state.personnelList.isEmpty() &&
                    (state.selectedUnit != null || state.searchQuery.isNotEmpty())) {
                    item {
                        Text(
                            "No personnel found",
                            color = OnSurfaceMid,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else if (state.personnelList.isEmpty()) {
                    item {
                        Text(
                            "Select a unit or search to find personnel",
                            color = OnSurfaceMid,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(
                    items = state.personnelList,
                    key = { it.officer.id }
                ) { officer ->
                    val isSelected = state.selectedOfficerIds.contains(officer.officer.id)
                    PersonnelCard(
                        officer = officer,
                        isSelected = isSelected,
                        onClick = { viewModel.toggleOfficerSelection(officer.officer.id) }
                    )
                }

                // ── 7. DELIVERY MODE ──────────────────────────────────
                item {
                    SectionLabel(
                        icon = Icons.Default.Tune,
                        label = "DELIVERY MODE",
                        tint = OnSurfaceMid
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DeliveryModePanel(
                        requireAck = state.requireAck,
                        isHighPriority = state.isHighPriority,
                        isEmergency = state.isEmergency,
                        onRequireAckChanged = viewModel::onRequireAckChanged,
                        onHighPriorityChanged = viewModel::onHighPriorityChanged,
                        onEmergencyChanged = viewModel::onEmergencyChanged
                    )
                }
            }

            // ── 8. STICKY DISPATCH BUTTON ─────────────────────────────
            DispatchFooter(
                state = state,
                onDispatch = viewModel::dispatch,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = tint,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun OperationalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    imeAction: ImeAction = ImeAction.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp, color = OnSurfaceMid.copy(alpha = 0.5f)) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = GoldenYellow.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        colors = operationalTextFieldColors()
    )
}

@Composable
private fun operationalTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurfaceMid,
    focusedBorderColor = GoldenYellow,
    unfocusedBorderColor = Surface1,
    focusedLabelColor = GoldenYellow,
    unfocusedLabelColor = OnSurfaceMid,
    cursorColor = GoldenYellow
)

@Composable
private fun UploadPromptBox(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Surface1, RoundedCornerShape(12.dp))
            .background(Surface1.copy(alpha = 0.3f))
            .clickable(enabled = !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = GoldenYellow, modifier = Modifier.size(24.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = GoldenYellow.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "TAP TO UPLOAD ORDER IMAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceMid,
                    letterSpacing = 0.5.sp
                )
                Text("JPG · PNG", fontSize = 10.sp, color = OnSurfaceMid.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun OrderImagePreview(
    localPath: String,
    isUploading: Boolean,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GoldenYellow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        AsyncImage(
            model = File(localPath),
            contentDescription = "Order Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
        )

        // Upload progress overlay
        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldenYellow, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("UPLOADING...", fontSize = 11.sp, color = GoldenYellow, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top-right label
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = NavyBlueDark.copy(alpha = 0.8f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                "ORDER IMAGE",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldenYellow,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                letterSpacing = 1.sp
            )
        }

        // Remove button
        if (!isUploading) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    label: String,
    options: List<UnitSummary>,
    selected: UnitSummary?,
    onSelect: (UnitSummary?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.unitName ?: "All Units",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = GoldenYellow.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = operationalTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(NavyBlueDark)
        ) {
            DropdownMenuItem(
                text = { Text("All Units", color = OnSurfaceMid, fontSize = 13.sp) },
                onClick = { onSelect(null); expanded = false }
            )
            options.forEach { unit ->
                DropdownMenuItem(
                    text = {
                        Text(
                            unit.unitName,
                            color = if (selected?.unitCode == unit.unitCode) GoldenYellow else OnSurface,
                            fontSize = 13.sp,
                            fontWeight = if (selected?.unitCode == unit.unitCode) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = { onSelect(unit); expanded = false },
                    leadingIcon = {
                        if (selected?.unitCode == unit.unitCode)
                            Icon(Icons.Default.Check, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected ?: "All Companies",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = {
                Icon(Icons.Default.Groups, contentDescription = null, tint = GoldenYellow.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = operationalTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(NavyBlueDark)
        ) {
            DropdownMenuItem(
                text = { Text("All Companies", color = OnSurfaceMid, fontSize = 13.sp) },
                onClick = { onSelect(null); expanded = false }
            )
            options.forEach { company ->
                DropdownMenuItem(
                    text = {
                        Text(
                            company,
                            color = if (selected == company) GoldenYellow else OnSurface,
                            fontSize = 13.sp,
                            fontWeight = if (selected == company) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = { onSelect(company); expanded = false },
                    leadingIcon = {
                        if (selected == company)
                            Icon(Icons.Default.Check, contentDescription = null, tint = GoldenYellow, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonnelCard(
    officer: com.kavach.app.data.local.entity.OfficerWithProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GoldenYellow.copy(alpha = 0.12f) else Surface1.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(1.dp, GoldenYellow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Checkbox-style indicator
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) GoldenYellow else Surface1),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = NavyBlueDark,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${officer.officer.pno} — ${officer.profile?.name?.uppercase(java.util.Locale.getDefault()) ?: "UNKNOWN"}",
                        color = if (isSelected) OnSurface else OnSurfaceMid,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(officer.officer.unitName)
                            officer.profile?.companyName?.let { append(" · $it") }
                            append(" · ${officer.officer.role}")
                        },
                        color = OnSurfaceMid.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryModePanel(
    requireAck: Boolean,
    isHighPriority: Boolean,
    isEmergency: Boolean,
    onRequireAckChanged: (Boolean) -> Unit,
    onHighPriorityChanged: (Boolean) -> Unit,
    onEmergencyChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DeliveryModeRow(
            label = "Require Acknowledgment",
            description = "Recipients must explicitly confirm receipt",
            checked = requireAck,
            onCheckedChange = onRequireAckChanged,
            tint = GoldenYellow
        )
        Divider(color = Surface1.copy(alpha = 0.5f), thickness = 0.5.dp)
        DeliveryModeRow(
            label = "High Priority",
            description = "Appears at top of recipient inbox",
            checked = isHighPriority,
            onCheckedChange = onHighPriorityChanged,
            tint = Color(0xFFFF9800)
        )
        Divider(color = Surface1.copy(alpha = 0.5f), thickness = 0.5.dp)
        DeliveryModeRow(
            label = "EMERGENCY",
            description = "Triggers alert tone on all recipient devices",
            checked = isEmergency,
            onCheckedChange = onEmergencyChanged,
            tint = DangerRed
        )
    }
}

@Composable
private fun DeliveryModeRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = if (checked) tint else OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(description, color = OnSurfaceMid, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tint,
                checkedTrackColor = tint.copy(alpha = 0.3f),
                uncheckedThumbColor = OnSurfaceMid,
                uncheckedTrackColor = Surface1
            )
        )
    }
}

@Composable
private fun DispatchFooter(
    state: DispatchUiState,
    onDispatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isProcessing = state.dispatchState == DispatchState.UPLOADING ||
            state.dispatchState == DispatchState.DISPATCHING

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, NavyBlueDark))
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Error message
        AnimatedVisibility(visible = state.error != null) {
            state.error?.let { error ->
                Text(
                    text = "⚠ $error",
                    color = DangerRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // State indicator
        if (state.selectedOfficerIds.isNotEmpty() || state.selectedUnit != null) {
            Text(
                text = buildString {
                    if (state.selectedUnit != null) append("→ ${state.selectedUnit.unitName}")
                    if (state.selectedCompany != null) append(" › ${state.selectedCompany}")
                    if (state.selectedOfficerIds.isNotEmpty()) append(" · ${state.selectedOfficerIds.size} selected")
                },
                color = GoldenYellow.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Button(
            onClick = onDispatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isEmergency) DangerRed else GoldenYellow,
                disabledContainerColor = Surface1
            ),
            enabled = !isProcessing
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        color = NavyBlueDark,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (state.dispatchState == DispatchState.UPLOADING) "UPLOADING IMAGE..." else "DISPATCHING...",
                        color = NavyBlueDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
                else -> {
                    Icon(
                        if (state.isEmergency) Icons.Default.NotificationImportant else Icons.Default.Campaign,
                        contentDescription = null,
                        tint = NavyBlueDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.isEmergency) "DISPATCH EMERGENCY ORDER" else "DISPATCH BROADCAST",
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

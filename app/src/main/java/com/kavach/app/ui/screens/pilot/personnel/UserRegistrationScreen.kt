package com.kavach.app.ui.screens.pilot.personnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.ui.components.FilterChipGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRegistrationScreen(
    viewModel: UserRegistrationViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (state.isEditMode) "EDIT PERSONNEL" else "REGISTER PERSONNEL",
                        fontSize = 16.sp, 
                        fontWeight = FontWeight.ExtraBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Basic Info Section
                SectionTitle("Identity & Authentication")
                
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onNameChange(it) },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value = state.pno,
                    onValueChange = { if (!state.isEditMode) viewModel.onPnoChange(it) },
                    label = { Text("PNO (Personnel Number)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isEditMode, // Read-only in Edit Mode
                    supportingText = { if (state.isEditMode) Text("Identity anchor cannot be changed") }
                )

                if (!state.isEditMode) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = { Text("Initial Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }

                Spacer(Modifier.height(8.dp))
                
                // 2. Operational Hierarchy Section
                SectionTitle("Operational Hierarchy")

                Text("System Role", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                RoleSelector(
                    selectedRole = state.role,
                    onRoleSelected = { viewModel.onRoleChange(it) }
                )

                Text("Rank", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                RankSelector(
                    selectedRank = state.rankId,
                    onRankSelected = { viewModel.onRankChange(it) }
                )

                Text("Unit Assignment", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                UnitTypeSelector(
                    selectedType = state.unitType,
                    onTypeSelected = { viewModel.onUnitTypeChange(it) }
                )

                if (state.unitType == "BATTALION") {
                    Text("Company", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    CompanySelector(
                        selectedCompany = state.companyId,
                        onCompanySelected = { viewModel.onCompanyChange(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 3. Contact Info Section
                SectionTitle("Contact Details")
                
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = { viewModel.onPhoneChange(it) },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.onEmailChange(it) },
                    label = { Text("Email Address (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(Modifier.height(24.dp))

                if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { viewModel.submit() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    enabled = !state.isSubmitting
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(if (state.isEditMode) Icons.Default.Save else Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(12.dp))
                        Text(if (state.isEditMode) "SAVE CHANGES" else "REGISTER USER", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun RoleSelector(selectedRole: String, onRoleSelected: (String) -> Unit) {
    val roles = listOf("USER", "PILOT", "COMMANDING_OFFICER")
    FilterChipGroup(
        options = roles,
        selectedOption = selectedRole,
        onOptionSelected = { role -> role?.let { onRoleSelected(it) } }
    )
}

@Composable
fun RankSelector(selectedRank: String, onRankSelected: (String) -> Unit) {
    val ranks = listOf(
        "CONSTABLE" to "CONSTABLE",
        "HEAD_CONSTABLE" to "HEAD_CONSTABLE",
        "PLATOON_COMMANDER" to "PLATOON COMMANDER",
        "COMPANY_COMMANDER" to "COMPANY COMMANDER",
        "ASSISTANT_COMMANDANT" to "ASST. COMMANDANT",
        "DEPUTY_COMMANDANT" to "DEPUTY COMMANDANT",
        "COMMANDANT" to "COMMANDANT"
    )
    FilterChipGroup(
        options = ranks.map { it.first },
        displayNames = ranks.map { it.second },
        selectedOption = selectedRank,
        onOptionSelected = { rank -> rank?.let { onRankSelected(it) } }
    )
}

@Composable
fun UnitTypeSelector(selectedType: String, onTypeSelected: (String) -> Unit) {
    val types = listOf("HQ", "RTC", "BATTALION")
    FilterChipGroup(
        options = types,
        selectedOption = selectedType,
        onOptionSelected = { type -> type?.let { onTypeSelected(it) } }
    )
}

@Composable
fun CompanySelector(selectedCompany: String?, onCompanySelected: (String?) -> Unit) {
    val companies = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    FilterChipGroup(
        options = companies,
        selectedOption = selectedCompany,
        onOptionSelected = onCompanySelected
    )
}

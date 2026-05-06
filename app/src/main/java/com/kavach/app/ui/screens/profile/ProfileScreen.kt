package com.kavach.app.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.remote.repository.AuthRepository
import com.kavach.app.ui.theme.*
import com.kavach.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────

data class ProfileState(
    val name             : String = "",
    val rank             : String = "",
    val unit             : String = "",
    val pno              : String = "",
    val role             : String = "USER",
    val email            : String = "",
    val disciplineScore  : Int    = 100,
    val level            : String = "L4",
    val autoAction       : String = "NONE",
    val isHighRisk       : Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionStore   : SessionDataStore,
    private val authRepository : AuthRepository
) : ViewModel() {

    private val _extra = MutableStateFlow(
        Triple(100, "L4", "USER") // score, level, role
    )

    val profileState: StateFlow<ProfileState> = combine(
        sessionStore.name, sessionStore.rank, sessionStore.unit, sessionStore.pno, _extra
    ) { name, rank, unit, pno, extra ->
        ProfileState(
            name            = name ?: "",
            rank            = rank ?: "",
            unit            = unit ?: "",
            pno             = pno  ?: "",
            role            = extra.third,
            disciplineScore = extra.first,
            level           = extra.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileState())

    init { fetchProfile() }

    fun fetchProfile() = viewModelScope.launch {
        runCatching {
            authRepository.getProfile()
        }.onSuccess { resource ->
            if (resource is Resource.Success) {
                val data = resource.data
                _extra.value = Triple(
                    data.disciplineScore,
                    data.level,
                    data.role
                )
            }
        }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }
}

// ── Screen ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout  : () -> Unit,
    onBack    : () -> Unit,
    viewModel : ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.profileState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    val (levelColor, levelLabel) = when (state.level) {
        "L1" -> DangerRed to "🔴 L1 – CRITICAL"
        "L2" -> Color(0xFFFF5722) to "🟠 L2 – SERIOUS"
        "L3" -> Color(0xFFFFB300) to "🟡 L3 – UNRELIABLE"
        else -> SuccessGreen to "🟢 L4 – COMPLIANT"
    }

    val roleLabel = when (state.role) {
        "SUPERUSER" -> "Superuser (HQ)"
        "ADMIN"     -> "Admin / CO"
        else        -> "Field Officer"
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title  = { Text("लॉगआउट") },
            text   = { Text("क्या आप वाकई लॉगआउट करना चाहते हैं?") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); onLogout() }) {
                    Text("हाँ", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("नहीं") }
            }
        )
    }

    Scaffold(
        containerColor = OfficialBackground,
        topBar = {
            TopAppBar(
                title = { Text("मेरी प्रोफाइल", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchProfile() }) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Avatar + Identity ─────────────────────────
            Box(
                modifier         = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(OfficialBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = TextSecondary, modifier = Modifier.size(56.dp))
            }

            Text(state.name.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge,
                color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(state.rank, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            // Role Badge
            Surface(
                color  = when (state.role) {
                    "SUPERUSER" -> Color(0xFF4A148C)
                    "ADMIN"     -> Color(0xFF1565C0)
                    else        -> SuccessGreen
                }.copy(alpha = 0.12f),
                shape  = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, when (state.role) {
                    "SUPERUSER" -> Color(0xFF7B1FA2)
                    "ADMIN"     -> Color(0xFF1976D2)
                    else        -> SuccessGreen
                }.copy(alpha = 0.5f))
            ) {
                Text(
                    roleLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Identity Info Card ────────────────────────
            Surface(color = SurfaceWhite, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileRow("PNO",   state.pno)
                    HorizontalDivider()
                    ProfileRow("यूनिट", state.unit.ifBlank { "—" })
                    HorizontalDivider()
                    ProfileRow("रैंक",  state.rank.ifBlank { "—" })
                    if (state.email.isNotBlank()) {
                        HorizontalDivider()
                        ProfileRow("Email", state.email)
                    }
                }
            }

            // ── Discipline Score Card ─────────────────────
            Surface(
                color  = levelColor.copy(alpha = 0.08f),
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, levelColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("अनुशासन रिकॉर्ड", style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(levelLabel, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Score: ${state.disciplineScore}",
                            style = MaterialTheme.typography.titleMedium,
                            color = levelColor, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress    = { state.disciplineScore / 100f },
                        modifier    = Modifier.fillMaxWidth().height(6.dp),
                        color       = levelColor,
                        trackColor  = levelColor.copy(alpha = 0.15f)
                    )
                }
            }

            // ── Actions ───────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.role in listOf("ADMIN", "SUPERUSER")) {
                    ActionTile("पासवर्ड बदलें", Icons.Default.Lock, TextPrimary) { /* Navigate */ }
                }
                ActionTile("PIN बदलें", Icons.Default.Pin, TextPrimary) { /* Navigate */ }
            }

            Spacer(Modifier.weight(1f))

            // ── Logout ───────────────────────────────────
            OutlinedButton(
                onClick  = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                border   = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = DangerRed)
                Spacer(Modifier.width(8.dp))
                Text("लॉगआउट", color = DangerRed)
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                       color: Color, onClick: () -> Unit) {
    Surface(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        color     = SurfaceWhite,
        shape     = RoundedCornerShape(10.dp),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = color,
                modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextSecondary,
                modifier = Modifier.size(16.dp))
        }
    }
}




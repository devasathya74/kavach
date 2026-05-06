package com.kavach.app.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.remote.dto.*
import com.kavach.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout  : () -> Unit,
    viewModel : AdminDashboardViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsState()
    val snackbarHostState  = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        containerColor = OfficialBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("COMMAND CONTROL PANEL", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Unit: PAC - All | Date: Today", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceWhite, tonalElevation = 8.dp) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Radar, null) },
                    label = { Text("Feed") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Security, null) },
                    label = { Text("Officers") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.QueryStats, null) },
                    label = { Text("Health") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Config") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.selectedTab) {
                0 -> LiveFeedTab(uiState)
                1 -> OfficersTab(uiState, viewModel)
                2 -> HealthTab(uiState)
                3 -> ConfigTab(uiState, viewModel)
            }
        }

        // Action loading overlay
        if (uiState.isActionLoading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SurfaceWhite) }
        }
    }
}

@Composable
private fun LiveFeedTab(uiState: AdminDashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LiveStatusStrip(uiState.officers)
        
        Text("REAL-TIME ANOMALY FEED", style = MaterialTheme.typography.labelLarge, color = TextSecondary, fontWeight = FontWeight.Bold)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(uiState.liveFeed) { event ->
                FeedItem(event)
            }
            if (uiState.liveFeed.isEmpty()) {
                item { 
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No active anomalies detected.", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedItem(event: LiveFeedEventDto) {
    val bgColor = when(event.severity) {
        "HIGH" -> DangerRed.copy(alpha = 0.05f)
        "MEDIUM" -> WarningOrange.copy(alpha = 0.05f)
        else -> SuccessGreen.copy(alpha = 0.05f)
    }
    val iconColor = when(event.severity) {
        "HIGH" -> DangerRed
        "MEDIUM" -> WarningOrange
        else -> SuccessGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(iconColor, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${event.type}: ${event.title}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${event.pno} | ${event.name}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text(event.timestamp.split("T").last().take(5), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun OfficersTab(uiState: AdminDashboardUiState, viewModel: AdminDashboardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            placeholder = { Text("Search PNO / Name...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        val filteredOfficers = uiState.officers.filter {
            it.pno.contains(uiState.searchQuery, ignoreCase = true) || 
            it.name.contains(uiState.searchQuery, ignoreCase = true)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredOfficers) { officer ->
                OfficerCard(officer) { pno, action, reason ->
                    viewModel.performAction(pno, action, reason)
                }
            }
        }
    }
}

@Composable
private fun OfficerCard(officer: AdminOfficerDto, onAction: (String, String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    val riskColor = when(officer.riskLevel) {
        "HIGH_RISK" -> DangerRed
        "SUSPICIOUS" -> WarningOrange
        else -> SuccessGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(OfficialBackground, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Text(officer.name.take(1), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${officer.pno} | ${officer.name}", fontWeight = FontWeight.Bold)
                    Text("${officer.rank} | ${officer.unit}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Badge(containerColor = riskColor.copy(alpha = 0.1f), contentColor = riskColor) {
                    Text(officer.riskLevel.replace("_", " "), Modifier.padding(horizontal = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Divider(Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color.LightGray)
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Trust Score", "${officer.disciplineScore}", TextPrimary)
                MetricItem("Predicted", "${officer.predictedScore}", if(officer.trajectory == "FALLING") DangerRed else SuccessGreen)
                MetricItem("Trend", officer.trajectory, if(officer.trajectory == "FALLING") DangerRed else SuccessGreen)
            }

            if (officer.anomalyReasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(color = WarningOrange.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text("🧠 AI EXPLANATION", style = MaterialTheme.typography.labelSmall, color = WarningOrange, fontWeight = FontWeight.Bold)
                        officer.anomalyReasons.take(2).forEach { reason ->
                            Text("• $reason", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text("CONTROL ACTION", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("🔍 DECISION TRACE", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Model Confidence", fontSize = 11.sp, color = TextSecondary)
                        Text(officer.confidenceScore, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    
                    if (officer.signalWeights.isNotEmpty()) {
                        officer.signalWeights.forEach { (signal, weight) ->
                            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(signal.uppercase(), fontSize = 10.sp, color = TextSecondary)
                                LinearProgressIndicator(
                                    progress = weight,
                                    modifier = Modifier.width(60.dp).height(4.dp).align(Alignment.CenterVertically),
                                    color = TextPrimary,
                                    trackColor = Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("BLOCK", DangerRed, Modifier.weight(1f)) { onAction(officer.pno, "BLOCK", "Admin") }
                        ActionButton("RESET", WarningOrange, Modifier.weight(1f)) { onAction(officer.pno, "RESET_DEVICE", "Admin") }
                        ActionButton("TRAIN", SuccessGreen, Modifier.weight(1f)) { onAction(officer.pno, "RESET_TRAINING", "Admin") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun ActionButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, color),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HealthTab(uiState: AdminDashboardUiState) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SYSTEM HEALTH & ACCURACY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        uiState.analytics?.let { analytics ->
            AnalyticsCard("Decision Accuracy Trend", analytics.dates, analytics.accuracy, SuccessGreen)
            AnalyticsCard("Anomaly Frequency", analytics.dates, analytics.anomalies.map { it.toFloat() }, WarningOrange)
        } ?: Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("Loading analytics...", color = TextSecondary)
        }
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
            Column(Modifier.padding(16.dp)) {
                Text("PILOT OPERATIONAL AUDIT", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                InsightRow("Total Decisions", "1,240", TextPrimary)
                InsightRow("Model Overrides", uiState.totalRollbacks.toString(), WarningOrange)
                InsightRow("False Positives", "0.2%", SuccessGreen)
            }
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, labels: List<String>, values: List<Float>, color: Color) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            // Simple visual representation of trend
            Row(Modifier.height(60.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                values.forEach { v ->
                    Box(Modifier.weight(1f).fillMaxHeight(v / 100f).background(color.copy(alpha = 0.5f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labels.firstOrNull() ?: "", fontSize = 10.sp, color = TextSecondary)
                Text(labels.lastOrNull() ?: "", fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ConfigTab(uiState: AdminDashboardUiState, viewModel: AdminDashboardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("REMOTE SYSTEM CONFIG", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        ConfigItem("ML Anomaly Engine", "Enable/Disable neural pattern matching", true) { }
        ConfigItem("Trajectory Prediction", "Preemptive risk signaling", true) { }
        ConfigItem("Strict Mode", "Force lockdown on suspicious activity", false) { }
        
        Spacer(Modifier.height(8.dp))
        Text("SENSITIVITY THRESHOLDS", style = MaterialTheme.typography.labelLarge, color = TextSecondary, fontWeight = FontWeight.Bold)
        
        ThresholdSlider("Anomaly Sensitivity", 0.75f)
        ThresholdSlider("Discipline Decay Rate", 0.30f)
    }
}

@Composable
private fun ConfigItem(title: String, desc: String, initial: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(checked = initial, onCheckedChange = onToggle)
    }
}

@Composable
private fun ThresholdSlider(label: String, value: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary))
    }
}

@Composable
private fun InsightRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveStatusStrip(officers: List<AdminOfficerDto>) {
    val l1 = officers.count { it.disciplineScore <= 20 }
    val l2 = officers.count { it.disciplineScore in 21..40 }
    val l3 = officers.count { it.disciplineScore in 41..60 }
    val l4 = officers.count { it.disciplineScore in 61..100 }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatBox("L1", l1.toString(), DangerRed, Modifier.weight(1f))
        StatBox("L2", l2.toString(), WarningOrange, Modifier.weight(1f))
        StatBox("L3", l3.toString(), Color(0xFFFFB300), Modifier.weight(1f))
        StatBox("L4", l4.toString(), SuccessGreen, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

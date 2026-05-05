package com.kavach.app.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.Order
import com.kavach.app.ui.theme.*
import kotlinx.coroutines.launch
/**
 * Order List Screen — shows all standing orders with acknowledgment status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    onOrderClick : (String) -> Unit,
    onBack       : () -> Unit,
    viewModel    : OrderListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = { Text("आदेश", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, null, tint = GoldenYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1)
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = GoldenYellow)
            }
            state.orders.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("कोई आदेश नहीं मिला", color = OnSurfaceMid)
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.orders, key = { it.id }) { order ->
                    OrderCard(order = order, onClick = { onOrderClick(order.id) })
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: Order, onClick: () -> Unit) {
    Surface(
        modifier       = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        color          = Surface2,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Filled.Description,
                contentDescription = null,
                tint               = if (order.isAcknowledged) SuccessGreen else GoldenYellow,
                modifier           = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text     = order.title,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text  = "जारीकर्ता: ${order.issuedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMid
                )
            }
            Spacer(Modifier.width(8.dp))
            if (order.isAcknowledged) {
                Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen)
            } else {
                Icon(Icons.Filled.RadioButtonUnchecked, null, tint = OnSurfaceLow)
            }
        }
    }
}

// ── Order Detail Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack    : () -> Unit,
    viewModel : OrderDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    // Secure Screen
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Dynamic Time & Behavior Tracking
    val orderContent = state.order?.contentText ?: ""
    val wordCount = orderContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    // dynamicTime = ~200 WPM
    val thresholdTime = kotlin.math.max(10_000L, (wordCount / 3L) * 1000L) 
    
    // Read Tracking logic
    var isScrolledToEnd by remember { mutableStateOf(false) }
    var readTime by remember { mutableStateOf(0L) }
    var canAcknowledge by remember { mutableStateOf(false) }

    // Quiz Control
    val quizTriggerPoint = remember { (30..70).random() / 100f }
    var quizShown by remember { mutableStateOf(false) }
    var quizPassed by remember { mutableStateOf(false) }
    var quizAttempts by remember { mutableStateOf(0) }

    val scrollState = androidx.compose.foundation.rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // ⏱️ Timer
    LaunchedEffect(state.order) {
        if (state.order == null || state.order!!.isAcknowledged) return@LaunchedEffect
        while (!canAcknowledge) {
            kotlinx.coroutines.delay(1000)
            readTime += 1000

            if (readTime >= thresholdTime && isScrolledToEnd && quizPassed) {
                canAcknowledge = true
            }
        }
    }

    // 📜 Scroll & Quiz detection
    LaunchedEffect(scrollState.value) {
        val progress = if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 1f
        
        // Mid-Read Quiz Trigger
        if (progress >= quizTriggerPoint && !quizShown && !quizPassed && orderContent.isNotBlank()) {
            quizShown = true
        }

        if (scrollState.value >= scrollState.maxValue - 50) {
            isScrolledToEnd = true
        }
    }

    // Mid-Read Quiz Dialog
    if (quizShown && !quizPassed) {
        AlertDialog(
            onDismissRequest = {}, // Force interaction
            title = { Text("पुष्टि आवश्यक", color = TextPrimary) },
            text = { Text("क्या आपने आदेश के मुख्य निर्देशों को ध्यान से पढ़ा है?") },
            confirmButton = {
                Button(
                    onClick = {
                        quizPassed = true
                        quizShown = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) { Text("हाँ, समझ गया") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        quizAttempts++
                        // Reset scroll if wrong answer
                        coroutineScope.launch { scrollState.scrollTo(0) }
                        quizShown = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("नहीं, दोबारा पढ़ूँगा") }
            }
        )
    }

    Scaffold(
        containerColor = OfficialBackground,
        topBar = {
            TopAppBar(
                title = { Text("आदेश विवरण", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
        bottomBar = {
            state.order?.let { order ->
                if (!order.isAcknowledged && !state.acknowledged) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (!isScrolledToEnd) "पूरा पढ़ें (नीचे स्क्रॉल करें)"
                            else if (!quizPassed && orderContent.isNotBlank()) "क्विज़ सत्यापन लंबित"
                            else if (readTime < thresholdTime) "कृपया ${(thresholdTime - readTime) / 1000} सेकंड और पढ़ें"
                            else "पढ़ना पूरा हुआ",
                            color = if (canAcknowledge) Color(0xFF388E3C) else DangerRed,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick  = { 
                                // Behavior Track Check
                                val expectedTime = thresholdTime
                                var score = 100
                                if (readTime < expectedTime * 0.6) score -= 30
                                if (quizAttempts > 1) score -= 10
                                
                                // Send readTime and behavior to ViewModel
                                viewModel.acknowledgeOrder(readTime) 
                            },
                            enabled  = canAcknowledge && !state.isAcknowledging,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = LightRed,
                                contentColor   = Color.White,
                                disabledContainerColor = Color.LightGray,
                                disabledContentColor = Color.DarkGray
                            )
                        ) {
                            if (state.isAcknowledging) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                            } else {
                                Text("स्वीकृत करें", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Surface(color = SurfaceWhite) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("स्वीकृत", color = SuccessGreen, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = GoldenYellow)
            }
            state.order == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("आदेश नहीं मिला", color = OnSurfaceMid)
            }
            else -> {
                val order = state.order!!
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                    ) {
                        Surface(color = Surface2, shape = RoundedCornerShape(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Shield, null, tint = GoldenYellow, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("जारीकर्ता", style = MaterialTheme.typography.labelSmall, color = OnSurfaceLow)
                                    Text(order.issuedBy, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                }
                                Spacer(Modifier.weight(1f))
                                val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(order.createdAt))
                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMid)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(order.title, style = MaterialTheme.typography.headlineMedium, color = OnSurface)

                        Spacer(Modifier.height(16.dp))

                        if (order.imageUrl != null) {
                            coil.compose.AsyncImage(
                                model = order.imageUrl,
                                contentDescription = "Order Image",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        if (!order.contentText.isNullOrBlank()) {
                            Surface(color = Surface2, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text     = order.contentText,
                                    style    = MaterialTheme.typography.bodyLarge,
                                    color    = OnSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    
                    com.kavach.app.ui.components.WatermarkOverlay(pno = state.viewerPno) 
                }
            }
        }
    }
}

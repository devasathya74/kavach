package com.kavach.app.ui.screens.user.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kavach.app.data.local.entity.OrderEntity
import com.kavach.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * MyOrdersScreen — My Assigned Orders.
 *
 * USER RULE: Shows ONLY orders in local DB (server has already filtered for this user).
 * No global order data. No personnel visibility. No issuer analytics.
 *
 * FIELD DESIGN:
 * - Large text, high contrast
 * - Tab bar: Pending / Acknowledged / Overdue
 * - One-tap ACK button per order
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyOrdersScreen(
    viewModel: MyOrdersViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // ACK confirmation snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionDone) {
        state.actionDone?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionDone()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NavyBlueDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MY ORDERS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "MISSION ASSIGNMENTS",
                            fontSize = 9.sp,
                            color = GoldenYellow,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GoldenYellow)
                    }
                },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            color = GoldenYellow,
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GoldenYellow)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlueDark,
                    titleContentColor = OnSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Tab Bar ────────────────────────────────────────────────
            OrderTabBar(
                selectedTab = state.selectedTab,
                pendingCount = state.pending.size,
                overdueCount = state.overdue.size,
                onTabSelected = viewModel::selectTab
            )

            // ── Order List ─────────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldenYellow)
                    }
                }
                state.displayList.isEmpty() -> {
                    EmptyOrderState(tab = state.selectedTab)
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = state.displayList,
                            key = { it.localId }
                        ) { order ->
                            OrderCard(
                                order = order,
                                onAcknowledge = {
                                    if (order.status == "PENDING") {
                                        viewModel.acknowledgeOrder(order.localId)
                                    }
                                }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OrderTabBar(
    selectedTab: OrderTab,
    pendingCount: Int,
    overdueCount: Int,
    onTabSelected: (OrderTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrderTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            val badge = when (tab) {
                OrderTab.PENDING -> pendingCount
                OrderTab.OVERDUE -> overdueCount
                else -> 0
            }

            FilterChip(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            tab.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (badge > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (tab == OrderTab.OVERDUE) DangerRed else Color(0xFFFF9800)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(badge.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GoldenYellow,
                    selectedLabelColor = NavyBlueDark,
                    containerColor = Surface1.copy(alpha = 0.5f),
                    labelColor = OnSurfaceMid
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Surface1,
                    selectedBorderColor = GoldenYellow
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(
    order: OrderEntity,
    onAcknowledge: () -> Unit
) {
    val isOverdue = order.status == "PENDING" &&
            order.expiresAt != null && order.expiresAt < System.currentTimeMillis()
    val isEmergency = order.type == "EMERGENCY"

    val borderColor = when {
        isEmergency -> DangerRed.copy(alpha = 0.6f)
        isOverdue -> Color(0xFFFF9800).copy(alpha = 0.6f)
        order.status == "ACKNOWLEDGED" -> Color(0xFF4ADE80).copy(alpha = 0.4f)
        else -> Color(0xFF60A5FA).copy(alpha = 0.3f)
    }

    val cardBg = when {
        isEmergency -> DangerRed.copy(alpha = 0.07f)
        isOverdue -> Color(0xFFFF9800).copy(alpha = 0.07f)
        else -> Surface1.copy(alpha = 0.5f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                OrderTypeBadge(type = order.type, isOverdue = isOverdue)

                // Status
                StatusBadge(status = order.status)
            }

            Spacer(Modifier.height(10.dp))

            // Title — large and readable
            Text(
                order.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = OnSurface,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(6.dp))

            // Content preview
            Text(
                order.content,
                fontSize = 13.sp,
                color = OnSurfaceMid,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(10.dp))

            // Meta row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ISSUED BY: ${order.issuedBy}",
                        fontSize = 10.sp,
                        color = OnSurfaceMid.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        formatTimestamp(order.issuedAt),
                        fontSize = 10.sp,
                        color = OnSurfaceMid.copy(alpha = 0.5f)
                    )
                }

                // ACK button — only shown for PENDING
                AnimatedVisibility(visible = order.status == "PENDING") {
                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEmergency) DangerRed else GoldenYellow
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NavyBlueDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ACKNOWLEDGE",
                            color = NavyBlueDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderTypeBadge(type: String, isOverdue: Boolean) {
    val (label, color) = when {
        type == "EMERGENCY" -> "EMERGENCY" to DangerRed
        isOverdue -> "OVERDUE" to Color(0xFFFF9800)
        type == "DRILL" -> "DRILL" to Color(0xFFA855F7)
        else -> "ORDER" to Color(0xFF60A5FA)
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "ACKNOWLEDGED" -> "✓ ACKNOWLEDGED" to Color(0xFF4ADE80)
        "PENDING" -> "PENDING ACK" to Color(0xFFFF9800)
        "EXPIRED" -> "EXPIRED" to Color(0xFF94A3B8)
        else -> status to OnSurfaceMid
    }
    Text(
        label,
        fontSize = 10.sp,
        color = color,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun EmptyOrderState(tab: OrderTab) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                when (tab) {
                    OrderTab.PENDING -> Icons.Default.Assignment
                    OrderTab.ACKNOWLEDGED -> Icons.Default.CheckCircle
                    OrderTab.OVERDUE -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = OnSurfaceMid.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                when (tab) {
                    OrderTab.PENDING -> "NO PENDING ORDERS"
                    OrderTab.ACKNOWLEDGED -> "NO ACKNOWLEDGED ORDERS"
                    OrderTab.OVERDUE -> "NO OVERDUE ORDERS"
                },
                color = OnSurfaceMid.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            Text(
                when (tab) {
                    OrderTab.PENDING -> "You are up to date."
                    else -> "Nothing here yet."
                },
                color = OnSurfaceMid.copy(alpha = 0.35f),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}

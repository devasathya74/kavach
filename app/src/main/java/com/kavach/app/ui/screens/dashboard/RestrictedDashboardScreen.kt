package com.kavach.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*

/**
 * RestrictedDashboardScreen
 *
 * Shown when the session cannot be recovered (network failure, token
 * expiry, integrity rejection, etc.). Gives the user two options:
 *  - Retry  → calls [onRetry] so the NavHostViewModel can re-attempt recovery
 *  - Logout → clears session and returns to Login
 */
@Composable
fun RestrictedDashboardScreen(
    onRetry  : () -> Unit,
    onLogout : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Icon ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = DangerRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Filled.Lock,
                contentDescription = null,
                tint               = DangerRed,
                modifier           = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Title ────────────────────────────────────────────────────
        Text(
            text       = "ACCESS RESTRICTED",
            fontSize   = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = DangerRed,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(12.dp))

        // ── Body ─────────────────────────────────────────────────────
        Text(
            text      = "Your session could not be verified.\n" +
                        "This may be due to a network issue, token expiry, " +
                        "or a device integrity failure.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = OnSurfaceMid,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(40.dp))

        // ── Divider ──────────────────────────────────────────────────
        HorizontalDivider(color = Surface3, thickness = 0.5.dp)

        Spacer(Modifier.height(32.dp))

        // ── Retry Button ─────────────────────────────────────────────
        Button(
            onClick  = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldenYellow,
                contentColor   = NavyBlueDark
            )
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text       = "RETRY CONNECTION",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Logout Button ────────────────────────────────────────────
        OutlinedButton(
            onClick  = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = DangerRed
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Filled.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text       = "LOGOUT",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── Footer note ──────────────────────────────────────────────
        Text(
            text      = "If this issue persists, contact your unit administrator.",
            style     = MaterialTheme.typography.labelSmall,
            color     = OnSurfaceLow,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RestrictedDashboardScreenPreview() {
    RestrictedDashboardScreen(onRetry = {}, onLogout = {})
}

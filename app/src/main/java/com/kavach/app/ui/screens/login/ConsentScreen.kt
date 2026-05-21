package com.kavach.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.NavyBlueDark
import com.kavach.app.ui.theme.GoldenYellow
import com.kavach.app.ui.theme.OnSurface
import com.kavach.app.ui.theme.OnSurfaceMid
import com.kavach.app.ui.theme.Surface1

@Composable
fun ConsentScreen(
    onAccepted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security Consent",
                tint = GoldenYellow,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SECURITY & OPERATIONAL GOVERNANCE CONSENT",
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface1),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "By accessing the Kavach Command & Field Operation Network, you acknowledge and agree to the following protocols:",
                        color = OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "1. Active Monitoring: All field broadcasts, incident uploads, and command actions are cryptographic, auditable, and tracked under our strict chain of custody protocols.\n\n" +
                               "2. Operational Isolation: Device integrity and connection statuses are actively monitored to protect local databases from unauthorized external access.\n\n" +
                               "3. Data Integrity: Local database mutations are authoritatively queued and automatically synced to prevent telemetry corruption or loss of critical field records.\n\n" +
                               "4. Revocation Policy: Security policy violations, attestation bypasses, or key tampering will trigger immediate cryptographic access revocation.",
                        color = OnSurfaceMid,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onAccepted,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = "I ACCEPT THE SECURITY PROTOCOLS",
                    color = NavyBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

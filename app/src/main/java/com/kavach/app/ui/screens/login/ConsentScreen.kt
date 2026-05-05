package com.kavach.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kavach.app.ui.theme.*

/**
 * ConsentScreen — shown ONCE on first app launch (before login).
 *
 * Legal requirements for deployment:
 *  1. User must acknowledge monitoring consent
 *  2. Data usage policy must be disclosed
 *  3. Device binding consent
 *  4. Audit trail disclosure
 *
 * Once accepted, consent flag saved in DataStore.
 * Cannot proceed to login without acceptance.
 *
 * Legal standing:
 *  Digital consent with timestamp = valid acceptance for government deployment.
 */
@Composable
fun ConsentScreen(
    onAccepted : () -> Unit
) {
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlueDark)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Header ────────────────────────────────────────
        Icon(
            imageVector        = Icons.Filled.Security,
            contentDescription = null,
            tint               = GoldenYellow,
            modifier           = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text       = "🛡️ KAVACH",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = GoldenYellow
        )
        Text(
            text  = "सुरक्षा एवं प्रशिक्षण प्रणाली",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMid
        )

        Spacer(Modifier.height(32.dp))

        // ── Consent document ──────────────────────────────
        Surface(
            color  = Surface1,
            shape  = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text       = "उपयोग नीति एवं सहमति",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = GoldenYellow
                )
                Spacer(Modifier.height(12.dp))

                ConsentPoint("📋 निगरानी अधिसूचना",
                    "इस एप्लीकेशन का उपयोग करके आप स्वीकार करते हैं कि आपकी सभी गतिविधियाँ " +
                    "(प्रशिक्षण प्रगति, आदेश स्वीकृति, डिवाइस जानकारी) रिकॉर्ड की जाएंगी।")

                ConsentPoint("📱 डिवाइस बाइंडिंग",
                    "यह एप्लीकेशन आपके डिवाइस के साथ बाध्य (bind) होगी। आपका PNO केवल एक " +
                    "अधिकृत डिवाइस पर उपयोग किया जा सकता है। दूसरे डिवाइस से लॉगिन " +
                    "स्वचालित रूप से अवरुद्ध होगा।")

                ConsentPoint("🎥 प्रशिक्षण नियंत्रण",
                    "वीडियो प्रशिक्षण को आगे नहीं किया जा सकता। प्रत्येक 15 सेकंड में " +
                    "आपकी उपस्थिति सत्यापित की जाएगी। अनियमित व्यवहार रिपोर्ट किया जाएगा।")

                ConsentPoint("🔏 डेटा उपयोग",
                    "एकत्रित डेटा केवल आंतरिक अनुशासन और प्रशिक्षण अनुपालन के लिए " +
                    "उपयोग होगा। बाहरी पक्षों के साथ साझा नहीं किया जाएगा।")

                ConsentPoint("⚖️ कानूनी वैधता",
                    "इस एप्लीकेशन में की गई डिजिटल स्वीकृति (Acknowledgment) एक वैध " +
                    "दस्तावेज़ी प्रमाण मानी जाएगी। इसे कागज़ी हस्ताक्षर के समकक्ष माना जाएगा।")
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Checkbox ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface2, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked         = checked,
                onCheckedChange = { checked = it },
                colors          = CheckboxDefaults.colors(
                    checkedColor         = GoldenYellow,
                    uncheckedColor       = OnSurfaceMid,
                    checkmarkColor       = NavyBlueDark
                )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text  = "मैंने उपरोक्त नीति पढ़ ली है और मैं सहमत हूँ।",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Accept button ─────────────────────────────────
        Button(
            onClick  = { if (checked) onAccepted() },
            enabled  = checked,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor        = GoldenYellow,
                contentColor          = NavyBlueDark,
                disabledContainerColor = Surface3,
                disabledContentColor   = OnSurfaceMid
            )
        ) {
            Text("स्वीकार करें और आगे बढ़ें", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ConsentPoint(title: String, body: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = GoldenYellow)
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = OnSurfaceMid)
    }
    HorizontalDivider(color = Surface3, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
}

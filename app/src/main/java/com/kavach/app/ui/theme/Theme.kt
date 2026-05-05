package com.kavach.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KavachColorScheme = lightColorScheme(
    primary          = LightRed,
    onPrimary        = SurfaceWhite,
    secondary        = LightGreen,
    onSecondary      = TextPrimary,
    background       = OfficialBackground,
    onBackground     = TextPrimary,
    surface          = SurfaceWhite,
    onSurface        = TextPrimary,
    surfaceVariant   = OfficialBackground,
    onSurfaceVariant = TextSecondary,
    error            = DangerRed,
    onError          = SurfaceWhite
)

@Composable
fun KavachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KavachColorScheme,
        typography  = KavachTypography,
        content     = content
    )
}

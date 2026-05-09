package com.kavach.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary          = LightPrimary,
    onPrimary        = LightSurface,
    secondary        = LightSecondary,
    onSecondary      = LightSurface,
    background       = LightBackground,
    onBackground     = LightTextPrimary,
    surface          = LightSurface,
    onSurface        = LightTextPrimary,
    surfaceVariant   = LightBackground,
    onSurfaceVariant = LightTextSecondary,
    error            = ColorError,
    onError          = LightSurface
)

private val DarkColorScheme = darkColorScheme(
    primary          = DarkPrimary,
    onPrimary        = DarkSurface,
    secondary        = DarkSecondary,
    onSecondary      = DarkSurface,
    background       = DarkBackground,
    onBackground     = DarkTextPrimary,
    surface          = DarkSurface,
    onSurface        = DarkTextPrimary,
    surfaceVariant   = DarkBackground,
    onSurfaceVariant = DarkTextSecondary,
    error            = ColorError,
    onError          = DarkSurface
)

@Composable
fun KavachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = KavachTypography,
        content     = content
    )
}

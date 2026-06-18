package com.aweb.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary        = AwebColors.PrimaryBlue,
    onPrimary      = Color.White,
    primaryContainer = AwebColors.NavyElevated,
    onPrimaryContainer = AwebColors.TextPrimary,
    secondary      = AwebColors.Teal,
    onSecondary    = AwebColors.InkDeep,
    tertiary       = AwebColors.Cyan,
    onTertiary     = AwebColors.InkDeep,
    background     = AwebColors.InkDeep,
    onBackground   = AwebColors.TextPrimary,
    surface        = AwebColors.Navy,
    onSurface      = AwebColors.TextPrimary,
    surfaceVariant = AwebColors.Surface,
    onSurfaceVariant = AwebColors.TextSecondary,
    outline        = AwebColors.Stroke,
    error          = AwebColors.Rose,
    onError        = Color.White,
)

@Composable
fun AwebTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content,
    )
}

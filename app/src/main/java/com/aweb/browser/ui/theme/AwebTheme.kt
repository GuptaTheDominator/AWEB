package com.aweb.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AWEB design system — dark theme, purple accent.
 * Compact palette for Phase 1; extend in later phases.
 */

private val AwebPurple        = Color(0xFF9C6FFF)
private val AwebPurpleVariant = Color(0xFF7B52D6)
private val AwebBackground    = Color(0xFF0F0F0F)
private val AwebSurface       = Color(0xFF1A1A1A)
private val AwebOnSurface     = Color(0xFFEAEAEA)
private val AwebError         = Color(0xFFCF6679)

private val DarkColorScheme = darkColorScheme(
    primary        = AwebPurple,
    onPrimary      = Color.Black,
    secondary      = AwebPurpleVariant,
    onSecondary    = Color.White,
    background     = AwebBackground,
    onBackground   = AwebOnSurface,
    surface        = AwebSurface,
    onSurface      = AwebOnSurface,
    error          = AwebError,
    onError        = Color.Black,
)

@Composable
fun AwebTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content,
    )
}

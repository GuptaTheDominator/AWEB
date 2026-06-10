package com.aweb.browser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.aweb.browser.ui.theme.AwebTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — Compose handles all navigation inside.
 *
 * Phase 1: shows [BrowserScreen] directly.
 * Phase 2+: NavHost with WorkspaceSidebar, SettingsScreen, etc.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AwebTheme {
                AwebNavHost()
            }
        }
    }
}

@Composable
fun AwebNavHost() {
    // Phase 1: single screen
    // Phase 2+: replace with NavHost and drawer/sidebar
    BrowserScreen()
}

@Preview(showBackground = true)
@Composable
private fun AwebPreview() {
    AwebTheme {
        // Preview stub — GeckoView cannot render in preview
    }
}

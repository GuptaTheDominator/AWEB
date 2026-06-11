package com.aweb.browser.ui.keepalive

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog shown when user tries to Keep Alive more tabs than the cap allows.
 *
 * Explains the situation and offers two actions:
 *  - Go to Settings (to raise the cap)
 *  - Dismiss
 */
@Composable
fun KeepAliveCapDialog(
    cap           : Int,
    onGoToSettings: () -> Unit,
    onDismiss     : () -> Unit,
) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color(0xFFCCCCCC),
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint     = KeepAliveColor,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                "Keep Alive Cap Reached",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You already have $cap Keep Alive tab${if (cap == 1) "" else "s"}, " +
                    "which is the current maximum.",
                    fontSize = 14.sp,
                )
                Text(
                    "To keep this tab alive, either:\n" +
                    "  • Disable Keep Alive on another tab, or\n" +
                    "  • Increase the cap in Settings → Memory → Max Keep Alive Tabs.",
                    fontSize = 13.sp,
                    color    = Color(0xFFAAAAAA),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KeepAliveBolt(size = KeepAliveIndicatorSize.MEDIUM, animated = false)
                    Text(
                        "Keep Alive tabs get highest priority after the active tab.",
                        fontSize = 11.sp,
                        color    = KeepAliveColor,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text(
                    "Open Settings",
                    color      = KeepAliveColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color(0xFF888888))
            }
        },
    )
}

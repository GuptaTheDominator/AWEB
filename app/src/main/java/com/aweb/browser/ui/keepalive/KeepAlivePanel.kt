package com.aweb.browser.ui.keepalive

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.data.entity.TabEntity

/**
 * Slide-up panel showing all Keep Alive tabs across the active workspace.
 *
 * Shown when user taps the ◆ bolt button in the toolbar.
 *
 * Features:
 *  - List of all Keep Alive tabs with their URL and lifecycle state
 *  - Tap to navigate to a tab
 *  - Toggle (disable) Keep Alive per tab
 *  - Cap indicator: "2 / 3 slots used"
 *  - Explanation of what Keep Alive does
 */
@Composable
fun KeepAlivePanel(
    keepAliveTabs : List<TabEntity>,
    cap           : Int,
    onSelectTab   : (TabEntity) -> Unit,
    onDisableKeepAlive : (TabEntity) -> Unit,
    onDismiss     : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    Surface(
        color  = Color(0xFF111111),
        shape  = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Handle ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF444444))
                )
            }

            // ── Header ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                KeepAliveBolt(
                    size     = KeepAliveIndicatorSize.LARGE,
                    animated = keepAliveTabs.isNotEmpty(),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Keep Alive Tabs",
                        color      = Color.White,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${keepAliveTabs.size} / $cap slots used",
                        color    = if (keepAliveTabs.size >= cap) Color(0xFFFF5C7A)
                                   else Color(0xFF888888),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = Color(0xFF888888))
                }
            }

            // ── Cap bar ───────────────────────────────────────────────────
            CapProgressBar(
                used  = keepAliveTabs.size,
                cap   = cap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )

            HorizontalDivider(color = Color(0xFF222222))

            // ── Tab list ──────────────────────────────────────────────────
            if (keepAliveTabs.isEmpty()) {
                EmptyKeepAliveHint(modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(keepAliveTabs, key = { it.id }) { tab ->
                        KeepAliveTabRow(
                            tab      = tab,
                            onSelect = { onSelectTab(tab); onDismiss() },
                            onDisable = { onDisableKeepAlive(tab) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Cap progress bar ───────────────────────────────────────────────────────

@Composable
private fun CapProgressBar(used: Int, cap: Int, modifier: Modifier = Modifier) {
    val fraction = if (cap > 0) used.toFloat() / cap else 0f
    val barColor = when {
        fraction >= 1f -> Color(0xFFFF5C7A)
        fraction >= 0.67f -> Color(0xFFFFC857)
        else -> KeepAliveColor
    }
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress   = { fraction.coerceIn(0f, 1f) },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color      = barColor,
            trackColor = Color(0xFF2A2A2A),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", color = Color(0xFF555555), fontSize = 9.sp)
            Text(
                if (fraction >= 1f) "Cap reached" else "$used of $cap",
                color    = barColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("$cap", color = Color(0xFF555555), fontSize = 9.sp)
        }
    }
}

// ── Single Keep Alive tab row ──────────────────────────────────────────────

@Composable
private fun KeepAliveTabRow(
    tab      : TabEntity,
    onSelect : () -> Unit,
    onDisable: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        KeepAliveBolt(size = KeepAliveIndicatorSize.MEDIUM, animated = true)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = tab.title.ifBlank { "New Tab" },
                color    = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = tab.url.removePrefix("https://").removePrefix("http://"),
                color    = Color(0xFF666666),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Disable button
        IconButton(
            onClick  = onDisable,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.FlashOff,
                contentDescription = "Disable Keep Alive",
                tint     = Color(0xFF666666),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────

@Composable
private fun EmptyKeepAliveHint(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(8.dp),
    ) {
        KeepAliveBolt(
            size     = KeepAliveIndicatorSize.LARGE,
            animated = false,
        )
        Text(
            "No Keep Alive tabs",
            color      = Color(0xFF888888),
            fontSize   = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Long-press any tab and choose\n\"Keep tab alive\" to keep it\nrunning in the background.",
            color    = Color(0xFF555555),
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aweb.browser.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import com.aweb.browser.ui.keepalive.KeepAliveBolt
import com.aweb.browser.ui.keepalive.KeepAliveColor
import com.aweb.browser.ui.keepalive.KeepAliveIndicatorSize

/**
 * Horizontal scrollable tab strip — sits between toolbar and web content.
 *
 * Phase 5 upgrades:
 *  - Keep Alive tabs show animated amber ◆ bolt instead of plain lifecycle icon
 *  - Long-press context menu now has "Keep tab alive" / "Disable Keep Alive"
 *    with the amber bolt icon and accurate toggle label
 *  - Tab chip border is amber for Keep Alive tabs, workspace-colour for active
 */
@Composable
fun TabStrip(
    tabs          : List<TabEntity>,
    activeTabId   : String?,
    workspaceColor: String,
    onSelectTab   : (TabEntity) -> Unit,
    onCloseTab    : (TabEntity) -> Unit,
    onNewTab      : () -> Unit,
    onPinTab      : (TabEntity, Boolean) -> Unit,
    onToggleKeepAlive: (TabEntity) -> Unit,
    modifier      : Modifier = Modifier,
) {
    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspaceColor))
    }.getOrDefault(Color(0xFF9C6FFF))

    val sortedTabs = remember(tabs) {
        tabs.sortedWith(
            compareByDescending<TabEntity> { it.isPinned }
                .thenByDescending { it.keepAlive }
                .thenBy { it.orderIndex }
        )
    }

    val listState = rememberLazyListState()

    val activeIndex = remember(sortedTabs, activeTabId) {
        sortedTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
    }
    LaunchedEffect(activeIndex) {
        if (sortedTabs.isNotEmpty()) listState.animateScrollToItem(activeIndex)
    }

    Surface(
        color     = Color(0xFF141414),
        modifier  = modifier.fillMaxWidth().height(40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                "${tabs.size}",
                color    = Color(0xFF555555),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            LazyRow(
                state                 = listState,
                modifier              = Modifier.weight(1f),
                contentPadding        = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(sortedTabs, key = { it.id }) { tab ->
                    TabChip(
                        tab              = tab,
                        isActive         = tab.id == activeTabId,
                        wsColor          = wsColor,
                        onSelect         = { onSelectTab(tab) },
                        onClose          = { onCloseTab(tab) },
                        onPin            = { onPinTab(tab, !tab.isPinned) },
                        onToggleKeepAlive = { onToggleKeepAlive(tab) },
                        modifier         = Modifier.animateItem(),
                    )
                }
            }

            IconButton(onClick = onNewTab, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Add, "New tab",
                    tint     = Color(0xFF888888),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Tab chip ───────────────────────────────────────────────────────────────

@Composable
private fun TabChip(
    tab              : TabEntity,
    isActive         : Boolean,
    wsColor          : Color,
    onSelect         : () -> Unit,
    onClose          : () -> Unit,
    onPin            : () -> Unit,
    onToggleKeepAlive: () -> Unit,
    modifier         : Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF252525) else Color(0xFF1A1A1A),
        label = "tabBg",
    )

    // Border: amber for keep-alive, workspace colour for active, none otherwise
    val borderColor = when {
        tab.keepAlive -> KeepAliveColor.copy(alpha = 0.7f)
        isActive      -> wsColor.copy(alpha = 0.6f)
        else          -> Color.Transparent
    }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(32.dp)
                .widthIn(min = 80.dp, max = 190.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .combinedClickable(
                    onClick     = onSelect,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 8.dp),
        ) {
            // Lifecycle / Keep Alive indicator
            if (tab.keepAlive) {
                KeepAliveBolt(
                    size     = KeepAliveIndicatorSize.SMALL,
                    animated = true,
                    modifier = Modifier.padding(end = 4.dp),
                )
            } else {
                Text(
                    lifecycleIcon(tab),
                    fontSize = 8.sp,
                    color    = lifecycleColor(tab, wsColor),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }

            // Title
            Text(
                text = tab.title.ifBlank {
                    tab.url.removePrefix("https://").removePrefix("http://")
                },
                color      = if (isActive) Color.White
                             else if (tab.keepAlive) KeepAliveColor.copy(alpha = 0.9f)
                             else Color(0xFF999999),
                fontSize   = 12.sp,
                fontWeight = if (isActive || tab.keepAlive) FontWeight.SemiBold
                             else FontWeight.Normal,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f),
            )

            // Close
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close, "Close tab",
                    tint     = Color(0xFF555555),
                    modifier = Modifier.size(10.dp),
                )
            }
        }

        // Context menu
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            modifier         = Modifier.background(Color(0xFF1E1E1E)),
        ) {
            // Pin
            DropdownMenuItem(
                text = {
                    Text(
                        if (tab.isPinned) "Unpin tab" else "Pin tab",
                        color = Color.White,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.PushPin, null, tint = Color(0xFF4FC3F7))
                },
                onClick = { showMenu = false; onPin() },
            )

            // Keep Alive toggle
            DropdownMenuItem(
                text = {
                    Text(
                        if (tab.keepAlive) "Disable Keep Alive" else "Keep tab alive",
                        color = if (tab.keepAlive) Color(0xFFAAAAAA) else Color.White,
                    )
                },
                leadingIcon = {
                    KeepAliveBolt(
                        size     = KeepAliveIndicatorSize.MEDIUM,
                        animated = !tab.keepAlive,
                    )
                },
                onClick = { showMenu = false; onToggleKeepAlive() },
            )

            HorizontalDivider(color = Color(0xFF333333))

            // Close
            DropdownMenuItem(
                text = { Text("Close tab", color = Color(0xFFCF6679)) },
                leadingIcon = {
                    Icon(Icons.Filled.Close, null, tint = Color(0xFFCF6679))
                },
                onClick = { showMenu = false; onClose() },
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun lifecycleIcon(tab: TabEntity) = when {
    tab.isPinned                               -> "📌"
    tab.lastLifecycleState == "active"         -> "●"
    tab.lastLifecycleState == "recent"         -> "◐"
    else                                       -> "○"
}

private fun lifecycleColor(tab: TabEntity, wsColor: Color) = when {
    tab.lastLifecycleState == "active"  -> wsColor
    tab.lastLifecycleState == "recent"  -> wsColor.copy(alpha = 0.55f)
    else                                -> Color(0xFF3A3A3A)
}

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
 * Horizontal tab strip — sits between the toolbar and the web content.
 *
 * Features:
 *  - Scrollable tab row (LazyRow)
 *  - Active tab highlighted
 *  - Pinned tabs always appear first (sorted in display)
 *  - Lifecycle state indicator (● Active  ○ Unloaded  ◆ Keep Alive  📌 Pinned)
 *  - Long-press tab → context menu (Pin / Keep Alive / Close)
 *  - "+" button on the right to open a new tab
 *  - Tab count badge
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
    onKeepAlive   : (TabEntity, Boolean) -> Unit,
    modifier      : Modifier = Modifier,
) {
    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspaceColor))
    }.getOrDefault(Color(0xFF9C6FFF))

    // Pinned tabs first, then by order index
    val sortedTabs = remember(tabs) {
        tabs.sortedWith(compareByDescending<TabEntity> { it.isPinned }.thenBy { it.orderIndex })
    }

    val listState = rememberLazyListState()

    // Auto-scroll to active tab when it changes
    val activeIndex = remember(sortedTabs, activeTabId) {
        sortedTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
    }
    LaunchedEffect(activeIndex) {
        if (sortedTabs.isNotEmpty()) listState.animateScrollToItem(activeIndex)
    }

    Surface(
        color          = Color(0xFF141414),
        tonalElevation = 0.dp,
        modifier       = modifier.fillMaxWidth().height(40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // Tab count badge
            Text(
                text     = "${tabs.size}",
                color    = Color(0xFF666666),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // Tab list
            LazyRow(
                state         = listState,
                modifier      = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(sortedTabs, key = { it.id }) { tab ->
                    TabChip(
                        tab          = tab,
                        isActive     = tab.id == activeTabId,
                        wsColor      = wsColor,
                        onSelect     = { onSelectTab(tab) },
                        onClose      = { onCloseTab(tab) },
                        onPin        = { onPinTab(tab, !tab.isPinned) },
                        onKeepAlive  = { onKeepAlive(tab, !tab.keepAlive) },
                    )
                }
            }

            // New tab button
            IconButton(
                onClick  = onNewTab,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New tab",
                    tint     = Color(0xFF888888),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Single tab chip ────────────────────────────────────────────────────────

@Composable
private fun TabChip(
    tab        : TabEntity,
    isActive   : Boolean,
    wsColor    : Color,
    onSelect   : () -> Unit,
    onClose    : () -> Unit,
    onPin      : () -> Unit,
    onKeepAlive: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF252525) else Color(0xFF1A1A1A),
        label       = "tabBg",
    )

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(32.dp)
                .widthIn(min = 80.dp, max = 180.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .then(
                    if (isActive) Modifier.border(
                        1.dp, wsColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp)
                    ) else Modifier
                )
                .combinedClickable(
                    onClick     = onSelect,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 8.dp),
        ) {
            // Lifecycle indicator
            Text(
                text     = lifecycleIcon(tab),
                fontSize = 8.sp,
                color    = lifecycleColor(tab, wsColor),
                modifier = Modifier.padding(end = 4.dp),
            )

            // Title
            Text(
                text     = tab.title.ifBlank { tab.url.removePrefix("https://").removePrefix("http://") },
                color    = if (isActive) Color.White else Color(0xFF999999),
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Close button
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close tab",
                    tint     = Color(0xFF666666),
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
            DropdownMenuItem(
                text = {
                    Text(
                        if (tab.isPinned) "Unpin tab" else "Pin tab",
                        color = Color.White,
                    )
                },
                leadingIcon = {
                    Icon(
                        if (tab.isPinned) Icons.Filled.PushPin else Icons.Filled.PushPin,
                        null,
                        tint = Color(0xFF4FC3F7),
                    )
                },
                onClick = { showMenu = false; onPin() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (tab.keepAlive) "Disable Keep Alive" else "Keep tab alive",
                        color = Color.White,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Bolt,
                        null,
                        tint = if (tab.keepAlive) Color(0xFFFFB74D) else Color(0xFF666666),
                    )
                },
                onClick = { showMenu = false; onKeepAlive() },
            )
            Divider(color = Color(0xFF333333))
            DropdownMenuItem(
                text = { Text("Close tab", color = Color(0xFFCF6679)) },
                leadingIcon = { Icon(Icons.Filled.Close, null, tint = Color(0xFFCF6679)) },
                onClick = { showMenu = false; onClose() },
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun lifecycleIcon(tab: TabEntity): String = when {
    tab.keepAlive                          -> "◆"
    tab.isPinned                           -> "📌"
    tab.lastLifecycleState == "active"     -> "●"
    tab.lastLifecycleState == "recent"     -> "◐"
    else                                   -> "○"
}

private fun lifecycleColor(tab: TabEntity, wsColor: Color): Color = when {
    tab.keepAlive                          -> Color(0xFFFFB74D)
    tab.lastLifecycleState == "active"     -> wsColor
    tab.lastLifecycleState == "recent"     -> wsColor.copy(alpha = 0.6f)
    else                                   -> Color(0xFF444444)
}

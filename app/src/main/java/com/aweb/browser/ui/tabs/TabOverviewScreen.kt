package com.aweb.browser.ui.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 * Full-screen tab overview grid — accessed by tapping the tab count button.
 *
 * Shows all tabs in a 2-column card grid.
 * Each card shows: title, url, lifecycle state badge, close button.
 * Long-press card → context menu (pin, keep alive, close).
 */
@Composable
fun TabOverviewScreen(
    tabs          : List<TabEntity>,
    activeTabId   : String?,
    workspaceColor: String,
    onSelectTab   : (TabEntity) -> Unit,
    onCloseTab    : (TabEntity) -> Unit,
    onNewTab      : () -> Unit,
    onPinTab      : (TabEntity, Boolean) -> Unit,
    onKeepAlive   : (TabEntity, Boolean) -> Unit,
    onCloseAll    : () -> Unit,
    onDismiss     : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspaceColor))
    }.getOrDefault(Color(0xFF9C6FFF))

    val sorted = remember(tabs) {
        tabs.sortedWith(compareByDescending<TabEntity> { it.isPinned }.thenBy { it.orderIndex })
    }

    Surface(
        color    = Color(0xFF0F0F0F),
        modifier = modifier.fillMaxSize(),
    ) {
        Column {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "${tabs.size} Tabs",
                    color      = Color.White,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCloseAll) {
                    Text("Close All", color = Color(0xFFCF6679), fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close overview", tint = Color.White)
                }
            }

            Divider(color = Color(0xFF2A2A2A))

            // Grid
            LazyVerticalGrid(
                columns            = GridCells.Fixed(2),
                contentPadding     = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier           = Modifier.weight(1f),
            ) {
                items(sorted, key = { it.id }) { tab ->
                    TabCard(
                        tab        = tab,
                        isActive   = tab.id == activeTabId,
                        wsColor    = wsColor,
                        onSelect   = { onSelectTab(tab); onDismiss() },
                        onClose    = { onCloseTab(tab) },
                        onPin      = { onPinTab(tab, !tab.isPinned) },
                        onKeepAlive = { onKeepAlive(tab, !tab.keepAlive) },
                    )
                }
            }

            // New tab FAB row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ExtendedFloatingActionButton(
                    onClick            = { onNewTab(); onDismiss() },
                    icon               = { Icon(Icons.Filled.Add, null) },
                    text               = { Text("New Tab") },
                    containerColor     = wsColor,
                    contentColor       = Color.Black,
                )
            }
        }
    }
}

// ── Tab card ───────────────────────────────────────────────────────────────

@Composable
private fun TabCard(
    tab        : TabEntity,
    isActive   : Boolean,
    wsColor    : Color,
    onSelect   : () -> Unit,
    onClose    : () -> Unit,
    onPin      : () -> Unit,
    onKeepAlive: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF252525) else Color(0xFF1A1A1A),
            ),
            border = if (isActive) BorderStroke(1.5.dp, wsColor.copy(alpha = 0.7f)) else null,
            shape  = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .combinedClickable(
                    onClick     = onSelect,
                    onLongClick = { showMenu = true },
                ),
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                Column(Modifier.fillMaxSize()) {
                    // State badges row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (tab.isPinned) {
                            StateBadge("📌 Pinned", Color(0xFF4FC3F7))
                        }
                        if (tab.keepAlive) {
                            StateBadge("◆ Keep Alive", Color(0xFFFFB74D))
                        }
                        if (!tab.isPinned && !tab.keepAlive) {
                            val (label, color) = when (tab.lastLifecycleState) {
                                "active"   -> "● Active"   to wsColor
                                "recent"   -> "◐ Recent"   to wsColor.copy(alpha = 0.7f)
                                else       -> "○ Unloaded" to Color(0xFF555555)
                            }
                            StateBadge(label, color)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Title
                    Text(
                        text     = tab.title.ifBlank { "New Tab" },
                        color    = if (isActive) Color.White else Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.weight(1f))

                    // URL
                    Text(
                        text     = tab.url.removePrefix("https://").removePrefix("http://"),
                        color    = Color(0xFF666666),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Close button top-right
                IconButton(
                    onClick  = onClose,
                    modifier = Modifier.size(24.dp).align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        "Close",
                        tint     = Color(0xFF666666),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            modifier         = Modifier.background(Color(0xFF1E1E1E)),
        ) {
            DropdownMenuItem(
                text = { Text(if (tab.isPinned) "Unpin" else "Pin tab", color = Color.White) },
                leadingIcon = { Icon(Icons.Filled.PushPin, null, tint = Color(0xFF4FC3F7)) },
                onClick = { showMenu = false; onPin() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (tab.keepAlive) "Disable Keep Alive" else "Keep Alive",
                        color = Color.White,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Bolt, null,
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

@Composable
private fun StateBadge(label: String, color: Color) {
    Text(
        text     = label,
        color    = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

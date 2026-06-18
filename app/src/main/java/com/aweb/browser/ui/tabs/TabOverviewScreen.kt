@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aweb.browser.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.aweb.browser.ui.keepalive.KeepAliveBadge
import com.aweb.browser.ui.keepalive.KeepAliveBolt
import com.aweb.browser.ui.keepalive.KeepAliveColor
import com.aweb.browser.ui.keepalive.KeepAliveIndicatorSize

/**
 * Full-screen tab overview grid.
 *
 * Phase 5 upgrades:
 *  - Keep Alive tabs get an amber [KeepAliveBadge] + animated bolt in their card
 *  - Context menu uses [KeepAliveBolt] icon and proper toggle label
 *  - Keep Alive cards have amber border instead of workspace colour border
 */
@Composable
fun TabOverviewScreen(
    tabs             : List<TabEntity>,
    activeTabId      : String?,
    workspaceColor   : String,
    onSelectTab      : (TabEntity) -> Unit,
    onCloseTab       : (TabEntity) -> Unit,
    onNewTab         : () -> Unit,
    onPinTab         : (TabEntity, Boolean) -> Unit,
    onToggleKeepAlive: (TabEntity) -> Unit,
    onCloseAll       : () -> Unit,
    onDismiss        : () -> Unit,
    modifier         : Modifier = Modifier,
) {
    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspaceColor))
    }.getOrDefault(Color(0xFF9C6FFF))

    val sorted = remember(tabs) {
        tabs.sortedWith(
            compareByDescending<TabEntity> { it.keepAlive }
                .thenByDescending { it.isPinned }
                .thenBy { it.orderIndex }
        )
    }

    val keepAliveCount = tabs.count { it.keepAlive }
    var showCloseAllConfirm by remember { mutableStateOf(false) }

    if (showCloseAllConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseAllConfirm = false },
            title = { Text("Close all tabs?", color = Color.White) },
            text = { Text("This will close every tab in this workspace.", color = Color(0xFFBBBBBB)) },
            confirmButton = {
                TextButton(onClick = { showCloseAllConfirm = false; onCloseAll(); onDismiss() }) {
                    Text("Close All", color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllConfirm = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E1E1E),
        )
    }

    Surface(color = Color(0xFF0F0F0F), modifier = modifier.fillMaxSize()) {
        Column {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(
                        "${tabs.size} Tabs",
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (keepAliveCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            KeepAliveBolt(
                                size     = KeepAliveIndicatorSize.MEDIUM,
                                animated = true,
                            )
                            Text(
                                "$keepAliveCount Keep Alive",
                                color    = KeepAliveColor,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showCloseAllConfirm = true }) {
                    Text("Close All", color = Color(0xFFCF6679), fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close overview", tint = Color.White)
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            LazyVerticalGrid(
                columns               = GridCells.Fixed(2),
                contentPadding        = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.weight(1f),
            ) {
                items(sorted, key = { it.id }) { tab ->
                    TabCard(
                        tab              = tab,
                        isActive         = tab.id == activeTabId,
                        wsColor          = wsColor,
                        onSelect         = { onSelectTab(tab); onDismiss() },
                        onClose          = { onCloseTab(tab) },
                        onPin            = { onPinTab(tab, !tab.isPinned) },
                        onToggleKeepAlive = { onToggleKeepAlive(tab) },
                    )
                }
            }

            // New Tab FAB
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ExtendedFloatingActionButton(
                    onClick        = { onNewTab(); onDismiss() },
                    icon           = { Icon(Icons.Filled.Add, null) },
                    text           = { Text("New Tab") },
                    containerColor = wsColor,
                    contentColor   = Color.Black,
                )
            }
        }
    }
}

// ── Tab card ───────────────────────────────────────────────────────────────

@Composable
private fun TabCard(
    tab              : TabEntity,
    isActive         : Boolean,
    wsColor          : Color,
    onSelect         : () -> Unit,
    onClose          : () -> Unit,
    onPin            : () -> Unit,
    onToggleKeepAlive: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val borderColor = when {
        tab.keepAlive -> KeepAliveColor.copy(alpha = 0.7f)
        isActive      -> wsColor.copy(alpha = 0.7f)
        else          -> Color.Transparent
    }

    Box {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF252525) else Color(0xFF1A1A1A),
            ),
            border = if (tab.keepAlive || isActive)
                BorderStroke(1.5.dp, borderColor) else null,
            shape  = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .combinedClickable(
                    onClick     = onSelect,
                    onLongClick = { showMenu = true },
                ),
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                Column(Modifier.fillMaxSize()) {

                    // Badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        if (tab.keepAlive) {
                            KeepAliveBadge()
                        }
                        if (tab.isPinned && !tab.keepAlive) {
                            StateBadge("📌 Pinned", Color(0xFF4FC3F7))
                        }
                        if (!tab.keepAlive && !tab.isPinned) {
                            val (label, color) = when (tab.lastLifecycleState) {
                                "active"   -> "● Active"   to wsColor
                                "recent"   -> "◐ Recent"   to wsColor.copy(alpha = 0.7f)
                                else       -> "○ Unloaded" to Color(0xFF555555)
                            }
                            StateBadge(label, color)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text       = tab.title.ifBlank { "New Tab" },
                        color      = when {
                            tab.keepAlive -> KeepAliveColor.copy(alpha = 0.9f)
                            isActive      -> Color.White
                            else          -> Color(0xFFCCCCCC)
                        },
                        fontSize   = 13.sp,
                        fontWeight = if (isActive || tab.keepAlive) FontWeight.SemiBold
                                     else FontWeight.Normal,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text     = tab.url.removePrefix("https://").removePrefix("http://"),
                        color    = Color(0xFF555555),
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
                        Icons.Filled.Close, "Close",
                        tint     = Color(0xFF555555),
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
                text = {
                    Text(
                        if (tab.isPinned) "Unpin" else "Pin tab",
                        color = Color.White,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.PushPin, null, tint = Color(0xFF4FC3F7)) },
                onClick = { showMenu = false; onPin() },
            )
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
        text       = label,
        color      = color,
        fontSize   = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

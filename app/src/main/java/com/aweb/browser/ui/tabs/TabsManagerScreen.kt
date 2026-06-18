package com.aweb.browser.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.ui.components.*
import com.aweb.browser.ui.theme.AwebColors

@Composable
fun TabsManagerScreen(
    tabs: List<TabEntity>,
    workspaces: List<WorkspaceEntity>,
    activeTabId: String?,
    onOpenTab: (TabEntity) -> Unit,
    onCloseTab: (TabEntity) -> Unit,
    onPinTab: (TabEntity, Boolean) -> Unit,
    onToggleKeepAlive: (TabEntity) -> Unit,
    onNewTab: () -> Unit,
) {
    AwebPageBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(onClick = onNewTab, containerColor = AwebColors.PrimaryBlue) {
                    Icon(Icons.Filled.Add, "New tab")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SectionTitle("Tabs", "Control active, pinned and Keep Alive sessions") }
                if (tabs.isEmpty()) {
                    item {
                        Surface(color = AwebColors.Surface.copy(alpha = 0.82f), shape = RoundedCornerShape(18.dp)) {
                            Text("No tabs in the active workspace yet.", color = AwebColors.TextSecondary, modifier = Modifier.padding(16.dp))
                        }
                    }
                } else {
                    items(tabs, key = { it.id }) { tab ->
                        TabManagerCard(
                            tab = tab,
                            workspace = workspaces.firstOrNull { it.id == tab.workspaceId },
                            isActive = tab.id == activeTabId,
                            onOpen = { onOpenTab(tab) },
                            onClose = { onCloseTab(tab) },
                            onPin = { onPinTab(tab, !tab.isPinned) },
                            onToggleKeepAlive = { onToggleKeepAlive(tab) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabManagerCard(
    tab: TabEntity,
    workspace: WorkspaceEntity?,
    isActive: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onToggleKeepAlive: () -> Unit,
) {
    Surface(
        color = if (isActive) AwebColors.NavyElevated.copy(alpha = 0.94f) else AwebColors.Surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) AwebColors.Cyan.copy(alpha = 0.45f) else AwebColors.Stroke),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (tab.keepAlive) Icons.Filled.Bolt else Icons.Filled.Language,
                    null,
                    tint = if (tab.keepAlive) AwebColors.Amber else AwebColors.Cyan,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tab.title.ifBlank { "New Tab" }, color = AwebColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tab.url.removePrefix("https://").removePrefix("http://"), color = AwebColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isActive) AwebPill("Active", AwebColors.Teal)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AwebPill(workspace?.name ?: "Workspace", AwebColors.PrimaryBlue)
                if (tab.isPinned) AwebPill("Pinned", AwebColors.Cyan)
                if (tab.keepAlive) AwebPill("Keep Alive", AwebColors.Amber)
                if (tab.url.startsWith("https://")) AwebPill("HTTPS", AwebColors.Teal)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = AwebColors.PrimaryBlue)) { Text("Open") }
                OutlinedButton(onClick = onPin) { Text(if (tab.isPinned) "Unpin" else "Pin", color = AwebColors.TextPrimary) }
                OutlinedButton(onClick = onToggleKeepAlive) { Text(if (tab.keepAlive) "Release" else "Keep", color = AwebColors.Amber) }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close tab", tint = AwebColors.Rose) }
            }
        }
    }
}

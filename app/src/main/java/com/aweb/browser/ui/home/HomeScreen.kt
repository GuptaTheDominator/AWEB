package com.aweb.browser.ui.home

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
fun HomeScreen(
    workspaces: List<WorkspaceEntity>,
    tabs: List<TabEntity>,
    activeWorkspace: WorkspaceEntity?,
    setupDone: Boolean,
    setupCompletedCount: Int,
    setupRequiredCount: Int,
    onOpenBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSpaces: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    AwebPageBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    activeWorkspace = activeWorkspace,
                    setupDone = setupDone,
                    setupCompletedCount = setupCompletedCount,
                    setupRequiredCount = setupRequiredCount,
                    onOpenBrowser = onOpenBrowser,
                    onOpenSettings = onOpenSettings,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("Workspaces", workspaces.size.toString(), AwebColors.PrimaryBlue, Modifier.weight(1f))
                    StatCard("Open tabs", tabs.size.toString(), AwebColors.Cyan, Modifier.weight(1f))
                    StatCard("Keep Alive", tabs.count { it.keepAlive }.toString(), AwebColors.Amber, Modifier.weight(1f))
                }
            }

            item {
                SectionTitle("Fast actions", "Jump directly into AWEB control surfaces")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AwebActionCard("Workspaces", "Manage isolated profiles", Icons.Filled.Workspaces, AwebColors.PrimaryBlue, onOpenSpaces, Modifier.weight(1f))
                        AwebActionCard("Tabs", "Review and control open tabs", Icons.Filled.Tab, AwebColors.Cyan, onOpenTabs, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AwebActionCard("Setup", "HyperOS survival checklist", Icons.Filled.Shield, AwebColors.Teal, onOpenSetup, Modifier.weight(1f))
                        AwebActionCard("Diagnostics", "Health and isolation checks", Icons.Filled.MonitorHeart, AwebColors.Amber, onOpenDiagnostics, Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionTitle("Recent tabs", activeWorkspace?.name ?: "No active workspace")
            }

            if (tabs.isEmpty()) {
                item {
                    Surface(color = AwebColors.Surface.copy(alpha = 0.82f), shape = RoundedCornerShape(18.dp)) {
                        Text("No tabs yet. Open the browser to start.", color = AwebColors.TextSecondary, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(tabs.take(6), key = { it.id }) { tab ->
                    RecentTabRow(tab = tab, onOpen = onOpenBrowser)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    activeWorkspace: WorkspaceEntity?,
    setupDone: Boolean,
    setupCompletedCount: Int,
    setupRequiredCount: Int,
    onOpenBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = AwebColors.NavyElevated.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkspaceAvatar(activeWorkspace?.name ?: "AWEB", activeWorkspace?.colorHex)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AWEB Command Center", color = AwebColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Isolated tablet browsing for HyperOS", color = AwebColors.TextSecondary, fontSize = 13.sp)
                }
                AwebPill(if (setupDone) "Ready" else "$setupCompletedCount/$setupRequiredCount setup", if (setupDone) AwebColors.Teal else AwebColors.Amber)
            }

            Spacer(Modifier.height(18.dp))
            SecurityLine("Active workspace: ${activeWorkspace?.name ?: "none"}")
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenBrowser,
                    colors = ButtonDefaults.buttonColors(containerColor = AwebColors.PrimaryBlue),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Filled.TravelExplore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open browser", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Filled.Settings, null, tint = AwebColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Settings", color = AwebColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun RecentTabRow(tab: TabEntity, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        color = AwebColors.Surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (tab.keepAlive) Icons.Filled.Bolt else Icons.Filled.Language,
                null,
                tint = if (tab.keepAlive) AwebColors.Amber else AwebColors.Cyan,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tab.title.ifBlank { "New Tab" }, color = AwebColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tab.url.removePrefix("https://").removePrefix("http://"), color = AwebColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tab.isPinned) AwebPill("Pinned", AwebColors.PrimaryBlue)
        }
    }
}

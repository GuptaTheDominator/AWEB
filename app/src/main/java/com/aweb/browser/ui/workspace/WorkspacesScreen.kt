package com.aweb.browser.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.ui.components.*
import com.aweb.browser.ui.theme.AwebColors

@Composable
fun WorkspacesScreen(
    workspaces: List<WorkspaceEntity>,
    activeWorkspace: WorkspaceEntity?,
    tabs: List<TabEntity>,
    onSwitchWorkspace: (WorkspaceEntity) -> Unit,
    onCreateWorkspace: (String, String) -> Unit,
    onClearWorkspace: (WorkspaceEntity) -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var clearTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }

    AwebPageBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreate = true }, containerColor = AwebColors.PrimaryBlue) {
                    Icon(Icons.Filled.Add, "Create workspace")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { SectionTitle("Spaces", "Manage isolated Gecko profiles and their tabs") }
                items(workspaces, key = { it.id }) { workspace ->
                    WorkspaceManagerCard(
                        workspace = workspace,
                        isActive = workspace.id == activeWorkspace?.id,
                        tabCount = tabs.count { it.workspaceId == workspace.id },
                        keepAliveCount = tabs.count { it.workspaceId == workspace.id && it.keepAlive },
                        onOpen = { onSwitchWorkspace(workspace); onOpenBrowser() },
                        onSwitch = { onSwitchWorkspace(workspace) },
                        onClear = { clearTarget = workspace },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateWorkspaceDialog(
            onConfirm = { name, color -> onCreateWorkspace(name, color); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    clearTarget?.let { ws ->
        ClearWorkspaceDataDialog(
            workspace = ws,
            onConfirm = { onClearWorkspace(ws); clearTarget = null },
            onDismiss = { clearTarget = null },
        )
    }
}

@Composable
private fun WorkspaceManagerCard(
    workspace: WorkspaceEntity,
    isActive: Boolean,
    tabCount: Int,
    keepAliveCount: Int,
    onOpen: () -> Unit,
    onSwitch: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = if (isActive) AwebColors.NavyElevated.copy(alpha = 0.94f) else AwebColors.Surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) AwebColors.PrimaryBlue.copy(alpha = 0.45f) else AwebColors.Stroke.copy(alpha = 0.7f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkspaceAvatar(workspace.name, workspace.colorHex)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(workspace.name, color = AwebColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("$tabCount open tab${if (tabCount == 1) "" else "s"} • $keepAliveCount Keep Alive", color = AwebColors.TextMuted, fontSize = 12.sp)
                }
                if (isActive) AwebPill("Active", AwebColors.Teal)
            }
            Spacer(Modifier.height(14.dp))
            SecurityLine("Isolated context: ${workspace.contextId.take(18)}…")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = AwebColors.PrimaryBlue)) {
                    Icon(Icons.Filled.TravelExplore, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
                OutlinedButton(onClick = onSwitch) { Text("Set active", color = AwebColors.TextPrimary) }
                OutlinedButton(onClick = onClear) { Text("Clear data", color = AwebColors.Amber) }
            }
        }
    }
}

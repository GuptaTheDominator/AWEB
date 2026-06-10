package com.aweb.browser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.ui.tabs.TabViewModel
import com.aweb.browser.ui.theme.AwebTheme
import com.aweb.browser.ui.workspace.*
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — owns the full app layout.
 *
 * Phase 3 layout:
 *  ┌──────────────────────────────────────────────────────────────┐
 *  │  WorkspaceSidebar (220dp) │  BrowserScreen                   │
 *  │                           │    ├── Toolbar (+ tab count btn) │
 *  │                           │    ├── TabStrip                  │
 *  │                           │    └── GeckoView                 │
 *  └──────────────────────────────────────────────────────────────┘
 *
 * [WorkspaceViewModel] drives the workspace list and active workspace.
 * [TabViewModel] drives the tab list and active tab for the active workspace.
 * When the workspace switches, TabViewModel.setWorkspace() is called so the
 * tab list re-loads automatically from Room.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwebTheme {
                AwebRootLayout()
            }
        }
    }
}

@Composable
fun AwebRootLayout() {
    val wsViewModel  : WorkspaceViewModel = hiltViewModel()
    val tabViewModel : TabViewModel       = hiltViewModel()

    val wsState by wsViewModel.uiState.collectAsState()

    // Keep TabViewModel in sync when active workspace changes
    LaunchedEffect(wsState.activeWorkspace) {
        wsState.activeWorkspace?.let { tabViewModel.setWorkspace(it) }
    }

    // Dialog targets
    var renameTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var clearTarget  by remember { mutableStateOf<WorkspaceEntity?>(null) }

    Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left sidebar ──────────────────────────────────────────────
            WorkspaceSidebar(
                workspaces      = wsState.workspaces,
                activeWorkspace = wsState.activeWorkspace,
                onSwitch        = { wsViewModel.switchWorkspace(it) },
                onNew           = { wsViewModel.showCreateDialog() },
                onRename        = { renameTarget = it },
                onDelete        = { wsViewModel.confirmDeleteWorkspace(it) },
                onClearData     = { clearTarget = it },
            )

            // ── Browser pane ──────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                BrowserScreen(
                    tabViewModel    = tabViewModel,
                    activeWorkspace = wsState.activeWorkspace,
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    if (wsState.showCreateDialog) {
        CreateWorkspaceDialog(
            onConfirm = { name, color -> wsViewModel.createWorkspace(name, color) },
            onDismiss = { wsViewModel.dismissCreateDialog() },
        )
    }

    renameTarget?.let { ws ->
        RenameWorkspaceDialog(
            workspace = ws,
            onConfirm = { newName -> wsViewModel.renameWorkspace(ws.id, newName); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }

    wsState.showDeleteDialog?.let { ws ->
        DeleteWorkspaceDialog(
            workspace = ws,
            onConfirm = { wsViewModel.deleteWorkspace(ws) },
            onDismiss = { wsViewModel.dismissDeleteDialog() },
        )
    }

    clearTarget?.let { ws ->
        ClearWorkspaceDataDialog(
            workspace = ws,
            onConfirm = { wsViewModel.clearWorkspaceData(ws); clearTarget = null },
            onDismiss = { clearTarget = null },
        )
    }
}

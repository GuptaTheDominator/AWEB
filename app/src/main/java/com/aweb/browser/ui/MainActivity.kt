package com.aweb.browser.ui

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.ui.theme.AwebTheme
import com.aweb.browser.ui.workspace.*
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — owns the full app layout.
 *
 * Phase 2 layout:
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  WorkspaceSidebar (220dp) │  BrowserScreen (rest)        │
 *  └──────────────────────────────────────────────────────────┘
 *
 * All workspace state flows through [WorkspaceViewModel].
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
    val viewModel: WorkspaceViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Dialog state for rename (needs the target workspace locally)
    var renameTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var clearTarget  by remember { mutableStateOf<WorkspaceEntity?>(null) }

    Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left sidebar ──────────────────────────────────────────────
            WorkspaceSidebar(
                workspaces      = uiState.workspaces,
                activeWorkspace = uiState.activeWorkspace,
                onSwitch        = { viewModel.switchWorkspace(it) },
                onNew           = { viewModel.showCreateDialog() },
                onRename        = { renameTarget = it },
                onDelete        = { viewModel.confirmDeleteWorkspace(it) },
                onClearData     = { clearTarget = it },
            )

            // ── Browser pane ──────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                BrowserScreen(viewModel = viewModel)
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    if (uiState.showCreateDialog) {
        CreateWorkspaceDialog(
            onConfirm = { name, color -> viewModel.createWorkspace(name, color) },
            onDismiss = { viewModel.dismissCreateDialog() },
        )
    }

    renameTarget?.let { ws ->
        RenameWorkspaceDialog(
            workspace = ws,
            onConfirm = { newName ->
                viewModel.renameWorkspace(ws.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    uiState.showDeleteDialog?.let { ws ->
        DeleteWorkspaceDialog(
            workspace = ws,
            onConfirm = { viewModel.deleteWorkspace(ws) },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }

    clearTarget?.let { ws ->
        ClearWorkspaceDataDialog(
            workspace = ws,
            onConfirm = {
                viewModel.clearWorkspaceData(ws)
                clearTarget = null
            },
            onDismiss = { clearTarget = null },
        )
    }
}

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
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.ui.tabs.TabViewModel
import com.aweb.browser.ui.theme.AwebTheme
import com.aweb.browser.ui.workspace.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity.
 *
 * Phase 4 additions:
 *  - Passes tab list + live session count to WorkspaceSidebar (MemoryStatusBar)
 *  - TabLifecycleManager wired through TabViewModel; settings changes auto-apply
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Needed to read liveSessionCount for the memory status bar
    @Inject lateinit var tabSessionManager: TabSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwebTheme {
                AwebRootLayout(tabSessionManager)
            }
        }
    }
}

@Composable
fun AwebRootLayout(tabSessionManager: TabSessionManager) {
    val wsViewModel  : WorkspaceViewModel = hiltViewModel()
    val tabViewModel : TabViewModel       = hiltViewModel()

    val wsState  by wsViewModel.uiState.collectAsState()
    val tabState by tabViewModel.uiState.collectAsState()

    // Keep TabViewModel in sync when active workspace changes
    LaunchedEffect(wsState.activeWorkspace) {
        wsState.activeWorkspace?.let { tabViewModel.setWorkspace(it) }
    }

    var renameTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var clearTarget  by remember { mutableStateOf<WorkspaceEntity?>(null) }

    Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left sidebar ──────────────────────────────────────────────
            WorkspaceSidebar(
                workspaces       = wsState.workspaces,
                activeWorkspace  = wsState.activeWorkspace,
                activeTabs       = tabState.tabs,
                liveSessionCount = tabSessionManager.liveSessionCount,
                onSwitch         = { wsViewModel.switchWorkspace(it) },
                onNew            = { wsViewModel.showCreateDialog() },
                onRename         = { renameTarget = it },
                onDelete         = { wsViewModel.confirmDeleteWorkspace(it) },
                onClearData      = { clearTarget = it },
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
            onConfirm = { name -> wsViewModel.renameWorkspace(ws.id, name); renameTarget = null },
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

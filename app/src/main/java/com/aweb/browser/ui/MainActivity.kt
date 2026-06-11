package com.aweb.browser.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.service.ServiceManager
import com.aweb.browser.ui.hardening.CrashRecoveryBanner
import com.aweb.browser.ui.hardening.DiagnosticsScreen
import com.aweb.browser.ui.hardening.HardeningViewModel
import com.aweb.browser.ui.settings.SettingsScreen
import com.aweb.browser.ui.settings.SettingsViewModel
import com.aweb.browser.ui.setup.HyperOsSetupScreen
import com.aweb.browser.ui.setup.SetupViewModel
import com.aweb.browser.ui.tabs.TabViewModel
import com.aweb.browser.ui.theme.AwebTheme
import com.aweb.browser.ui.workspace.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tabSessionManager: TabSessionManager
    @Inject lateinit var serviceManager   : ServiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwebTheme {
                AwebRootLayout(
                    tabSessionManager = tabSessionManager,
                    serviceManager    = serviceManager,
                    onKeepScreenAwake = { keep ->
                        if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                )
            }
        }
    }
}

@Composable
fun AwebRootLayout(
    tabSessionManager : TabSessionManager,
    serviceManager    : ServiceManager,
    onKeepScreenAwake : (Boolean) -> Unit,
) {
    val wsViewModel       : WorkspaceViewModel = hiltViewModel()
    val tabViewModel      : TabViewModel       = hiltViewModel()
    val settingsViewModel : SettingsViewModel  = hiltViewModel()
    val setupViewModel    : SetupViewModel     = hiltViewModel()
    val hardeningViewModel: HardeningViewModel = hiltViewModel()

    val wsState        by wsViewModel.uiState.collectAsState()
    val tabState       by tabViewModel.uiState.collectAsState()
    val settingsState  by settingsViewModel.uiState.collectAsState()
    val setupDone      by setupViewModel.setupDone.collectAsState()
    val hardeningState by hardeningViewModel.uiState.collectAsState()

    // Keep screen awake flag
    LaunchedEffect(settingsState.keepScreenAwake) {
        onKeepScreenAwake(settingsState.keepScreenAwake)
    }

    // Sync TabViewModel with active workspace
    LaunchedEffect(wsState.activeWorkspace) {
        wsState.activeWorkspace?.let { tabViewModel.setWorkspace(it) }
    }

    // Update foreground service notification when tabs change
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(tabState.tabs) {
        serviceManager.requestNotificationUpdate(context)
    }

    // Mark session clean when Activity leaves foreground
    DisposableEffect(Unit) {
        onDispose { hardeningViewModel.markClean() }
    }

    var showSettings    by remember { mutableStateOf(false) }
    var showSetup       by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var renameTarget    by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var clearTarget     by remember { mutableStateOf<WorkspaceEntity?>(null) }

    // Show setup guide on first launch only
    LaunchedEffect(setupDone) {
        if (!setupDone) showSetup = true
    }

    Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {

            // Main layout: sidebar + browser
            Row(Modifier.fillMaxSize()) {
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
                    onOpenSettings   = { showSettings = true },
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    BrowserScreen(
                        tabViewModel    = tabViewModel,
                        activeWorkspace = wsState.activeWorkspace,
                    )
                }
            }

            // Crash recovery banner
            if (hardeningState.showCrashBanner) {
                hardeningState.crashInfo?.let { info ->
                    CrashRecoveryBanner(
                        crashMessage = info.lastCrashMessage,
                        crashTime    = info.lastCrashTime,
                        onDismiss    = { hardeningViewModel.dismissCrashBanner() },
                        modifier     = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(start = 220.dp),
                    )
                }
            }

            // Settings overlay
            AnimatedVisibility(
                visible  = showSettings,
                enter    = slideInHorizontally { it } + fadeIn(),
                exit     = slideOutHorizontally { it } + fadeOut(),
            ) {
                Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onDismiss         = { showSettings = false },
                        viewModel         = settingsViewModel,
                        onOpenSetup       = { showSetup = true },
                        onOpenDiagnostics = { showDiagnostics = true },
                    )
                }
            }

            // HyperOS setup overlay
            AnimatedVisibility(
                visible  = showSetup,
                enter    = slideInVertically { -it } + fadeIn(),
                exit     = slideOutVertically { -it } + fadeOut(),
            ) {
                Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
                    HyperOsSetupScreen(
                        onDismiss = { showSetup = false },
                        onAllDone = { setupViewModel.markSetupDone() },
                    )
                }
            }

            // Diagnostics overlay
            AnimatedVisibility(
                visible  = showDiagnostics,
                enter    = slideInHorizontally { it } + fadeIn(),
                exit     = slideOutHorizontally { it } + fadeOut(),
            ) {
                Surface(color = Color(0xFF0F0F0F), modifier = Modifier.fillMaxSize()) {
                    DiagnosticsScreen(
                        onDismiss = { showDiagnostics = false },
                        viewModel = hardeningViewModel,
                    )
                }
            }
        }
    }

    // Workspace dialogs
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

package com.aweb.browser.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.service.ServiceManager
import com.aweb.browser.ui.hardening.CrashRecoveryBanner
import com.aweb.browser.ui.hardening.DiagnosticsScreen
import com.aweb.browser.ui.hardening.HardeningViewModel
import com.aweb.browser.ui.home.HomeScreen
import com.aweb.browser.ui.settings.SettingsScreen
import com.aweb.browser.ui.settings.SettingsViewModel
import com.aweb.browser.ui.setup.HyperOsSetupScreen
import com.aweb.browser.ui.setup.HyperOsSetupStepIds
import com.aweb.browser.ui.setup.SetupViewModel
import com.aweb.browser.ui.tabs.TabViewModel
import com.aweb.browser.ui.tabs.TabsManagerScreen
import com.aweb.browser.ui.theme.AwebColors
import com.aweb.browser.ui.theme.AwebTheme
import com.aweb.browser.ui.workspace.WorkspaceViewModel
import com.aweb.browser.ui.workspace.WorkspacesScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tabSessionManager: TabSessionManager
    @Inject lateinit var serviceManager   : ServiceManager

    private val externalUrl = MutableStateFlow<String?>(null)
    private var keepAwakePreference = false
    private var isCharging = false

    private val chargingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            updateKeepScreenAwakeFlag()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(chargingReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handleExternalUrlIntent(intent)
        setContent {
            val pendingExternalUrl by externalUrl.collectAsState()
            AwebTheme {
                AwebRootLayout(
                    tabSessionManager = tabSessionManager,
                    serviceManager    = serviceManager,
                    externalUrl       = pendingExternalUrl,
                    onExternalUrlConsumed = { externalUrl.value = null },
                    onKeepScreenAwake = { keep ->
                        keepAwakePreference = keep
                        updateKeepScreenAwakeFlag()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalUrlIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(chargingReceiver) }
        super.onDestroy()
    }

    private fun handleExternalUrlIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString?.takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: return
        externalUrl.value = url
    }

    private fun updateKeepScreenAwakeFlag() {
        if (keepAwakePreference && isCharging) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private enum class AwebRoute(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("home", "Home", Icons.Filled.Dashboard),
    Spaces("spaces", "Spaces", Icons.Filled.Workspaces),
    Browse("browse", "Browse", Icons.Filled.TravelExplore),
    Tabs("tabs", "Tabs", Icons.Filled.Tab),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

private object SecondaryRoute {
    const val Setup = "setup"
    const val Diagnostics = "diagnostics"
}

@Composable
fun AwebRootLayout(
    tabSessionManager : TabSessionManager,
    serviceManager    : ServiceManager,
    externalUrl       : String? = null,
    onExternalUrlConsumed: () -> Unit = {},
    onKeepScreenAwake : (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val wsViewModel       : WorkspaceViewModel = hiltViewModel()
    val tabViewModel      : TabViewModel       = hiltViewModel()
    val settingsViewModel : SettingsViewModel  = hiltViewModel()
    val setupViewModel    : SetupViewModel     = hiltViewModel()
    val hardeningViewModel: HardeningViewModel = hiltViewModel()

    val wsState        by wsViewModel.uiState.collectAsState()
    val tabState       by tabViewModel.uiState.collectAsState()
    val settingsState  by settingsViewModel.uiState.collectAsState()
    val setupDone      by setupViewModel.setupDone.collectAsState()
    val setupCompletedSteps by setupViewModel.completedSteps.collectAsState()
    val hardeningState by hardeningViewModel.uiState.collectAsState()

    fun navigatePrimary(route: AwebRoute) {
        navController.navigate(route.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateSecondary(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    LaunchedEffect(settingsState.keepScreenAwake) {
        onKeepScreenAwake(settingsState.keepScreenAwake)
    }

    LaunchedEffect(wsState.activeWorkspace) {
        wsState.activeWorkspace?.let { tabViewModel.setWorkspace(it) }
    }

    LaunchedEffect(externalUrl, wsState.activeWorkspace?.id) {
        val url = externalUrl
        if (url != null && wsState.activeWorkspace != null) {
            tabViewModel.openNewTab(url)
            onExternalUrlConsumed()
            navigatePrimary(AwebRoute.Browse)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationStateKey = remember(tabState.tabs) {
        "${tabState.tabs.size}:${tabState.tabs.count { it.keepAlive }}"
    }
    LaunchedEffect(notificationStateKey) {
        serviceManager.requestNotificationUpdate(context)
    }

    DisposableEffect(Unit) {
        onDispose { hardeningViewModel.markClean() }
    }

    var setupPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(setupDone) {
        if (!setupDone && !setupPrompted) {
            setupPrompted = true
            navigateSecondary(SecondaryRoute.Setup)
        }
    }

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomBar = AwebRoute.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = AwebColors.InkDeep,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = AwebColors.Navy, contentColor = AwebColors.TextPrimary) {
                    AwebRoute.entries.forEach { route ->
                        NavigationBarItem(
                            selected = currentRoute == route.route,
                            onClick = { navigatePrimary(route) },
                            icon = { Icon(route.icon, contentDescription = route.label) },
                            label = { Text(route.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AwebColors.Cyan,
                                selectedTextColor = AwebColors.Cyan,
                                indicatorColor = AwebColors.SurfaceHigh,
                                unselectedIconColor = AwebColors.TextMuted,
                                unselectedTextColor = AwebColors.TextMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = AwebRoute.Home.route) {
                composable(AwebRoute.Home.route) {
                    HomeScreen(
                        workspaces = wsState.workspaces,
                        tabs = wsState.allTabs,
                        activeWorkspace = wsState.activeWorkspace,
                        setupDone = setupDone,
                        setupCompletedCount = setupCompletedSteps.count { it in HyperOsSetupStepIds.REQUIRED },
                        setupRequiredCount = HyperOsSetupStepIds.REQUIRED.size,
                        onOpenBrowser = { navigatePrimary(AwebRoute.Browse) },
                        onOpenSettings = { navigatePrimary(AwebRoute.Settings) },
                        onOpenSpaces = { navigatePrimary(AwebRoute.Spaces) },
                        onOpenTabs = { navigatePrimary(AwebRoute.Tabs) },
                        onOpenSetup = { navigateSecondary(SecondaryRoute.Setup) },
                        onOpenDiagnostics = { navigateSecondary(SecondaryRoute.Diagnostics) },
                    )
                }
                composable(AwebRoute.Spaces.route) {
                    WorkspacesScreen(
                        workspaces = wsState.workspaces,
                        activeWorkspace = wsState.activeWorkspace,
                        tabs = wsState.allTabs,
                        onSwitchWorkspace = { wsViewModel.switchWorkspace(it) },
                        onCreateWorkspace = { name, color -> wsViewModel.createWorkspace(name, color) },
                        onClearWorkspace = { wsViewModel.clearWorkspaceData(it) },
                        onOpenBrowser = { navigatePrimary(AwebRoute.Browse) },
                    )
                }
                composable(AwebRoute.Browse.route) {
                    BrowserScreen(
                        tabViewModel = tabViewModel,
                        activeWorkspace = wsState.activeWorkspace,
                    )
                }
                composable(AwebRoute.Tabs.route) {
                    TabsManagerScreen(
                        tabs = tabState.tabs,
                        workspaces = wsState.workspaces,
                        activeTabId = tabState.activeTab?.id,
                        onOpenTab = { tab -> tabViewModel.selectTab(tab); navigatePrimary(AwebRoute.Browse) },
                        onCloseTab = { tabViewModel.closeTab(it) },
                        onPinTab = { tab, pinned -> tabViewModel.setPinned(tab, pinned) },
                        onToggleKeepAlive = { tabViewModel.toggleKeepAlive(it) },
                        onNewTab = { tabViewModel.openNewTab(); navigatePrimary(AwebRoute.Browse) },
                    )
                }
                composable(AwebRoute.Settings.route) {
                    SettingsScreen(
                        onDismiss = { navigatePrimary(AwebRoute.Home) },
                        viewModel = settingsViewModel,
                        onOpenSetup = { navigateSecondary(SecondaryRoute.Setup) },
                        onOpenDiagnostics = { navigateSecondary(SecondaryRoute.Diagnostics) },
                    )
                }
                composable(SecondaryRoute.Setup) {
                    HyperOsSetupScreen(
                        onDismiss = { navController.popBackStack() },
                        onAllDone = { setupViewModel.markSetupDone() },
                        completedSteps = setupCompletedSteps,
                        onStepDoneChange = { stepId, done -> setupViewModel.setStepDone(stepId, done) },
                    )
                }
                composable(SecondaryRoute.Diagnostics) {
                    DiagnosticsScreen(
                        onDismiss = { navController.popBackStack() },
                        viewModel = hardeningViewModel,
                    )
                }
            }

            if (hardeningState.showCrashBanner) {
                hardeningState.crashInfo?.let { info ->
                    CrashRecoveryBanner(
                        crashMessage = info.lastCrashMessage,
                        crashTime = info.lastCrashTime,
                        onDismiss = { hardeningViewModel.dismissCrashBanner() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

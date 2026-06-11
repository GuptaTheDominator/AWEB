package com.aweb.browser.ui

import android.Manifest
import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.aweb.browser.browser.BrowserPermissionHandler
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.ui.browser.*
import com.aweb.browser.ui.keepalive.*
import com.aweb.browser.ui.tabs.TabOverviewScreen
import com.aweb.browser.ui.tabs.TabStrip
import com.aweb.browser.ui.tabs.TabViewModel
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Phase 8 BrowserScreen — fully featured daily-use browser.
 *
 * New in Phase 8:
 *  - Bookmark star button in toolbar (filled = bookmarked, outline = not)
 *  - Bookmarks panel (slide-up)
 *  - Find-in-page bar (slide-up from bottom)
 *  - Desktop/Mobile UA toggle in overflow menu
 *  - Fullscreen video support (hides toolbar + tab strip)
 *  - Permission dialogs (camera, mic, location, notification, download)
 *  - File upload picker via ActivityResult
 *  - Security lock icon in URL bar (🔒 / 🔓)
 *  - Overflow menu (Find / Desktop mode / Bookmarks / Share)
 */
@Composable
fun BrowserScreen(
    tabViewModel    : TabViewModel,
    activeWorkspace : WorkspaceEntity?,
    featureViewModel: BrowserFeatureViewModel = hiltViewModel(),
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    val tabState    by tabViewModel.uiState.collectAsState()
    val session     = tabState.activeSession

    val url         by (session?.url         ?: emptyStateFlow("")).collectAsState()
    val title       by (session?.title       ?: emptyStateFlow("New Tab")).collectAsState()
    val loading     by (session?.loading     ?: emptyStateFlow(false)).collectAsState()
    val progress    by (session?.progress    ?: emptyStateFlow(0)).collectAsState()
    val canGoBack   by (session?.canGoBack   ?: emptyStateFlow(false)).collectAsState()
    val canGoFwd    by (session?.canGoForward ?: emptyStateFlow(false)).collectAsState()
    val isSecure    by (session?.isSecure    ?: emptyStateFlow(false)).collectAsState()

    val wsColor     = activeWorkspace?.colorHex ?: "#9C6FFF"

    // ── Feature state ─────────────────────────────────────────────────────
    val isBookmarked  by featureViewModel.isBookmarked.collectAsState()
    val bookmarks     by featureViewModel.bookmarks.collectAsState()
    val findVisible   by featureViewModel.findVisible.collectAsState()
    val findResult    by featureViewModel.findResult.collectAsState()
    val uaMode        by featureViewModel.uaMode.collectAsState()
    val isFullscreen  by featureViewModel.isFullscreen.collectAsState()
    val showBookmarks by featureViewModel.showBookmarks.collectAsState()
    val keepAliveCount = tabState.tabs.count { it.keepAlive }

    // Check bookmark status whenever URL changes
    LaunchedEffect(url) { if (url.isNotBlank()) featureViewModel.checkBookmark(url) }

    // Attach session to FindInPageHandler
    LaunchedEffect(session) {
        session?.session?.let { featureViewModel.attachFindSession(it) }
    }

    // Fullscreen callback from GeckoView
    LaunchedEffect(session) {
        session?.onFullscreenChange = { fs ->
            if (fs) activity?.let { featureViewModel.enterFullscreen(it) }
            else    activity?.let { featureViewModel.exitFullscreen(it) }
        }
    }

    // ── Overlay / dialog state ────────────────────────────────────────────
    var showTabOverview    by remember { mutableStateOf(false) }
    var showKaPanel        by remember { mutableStateOf(false) }
    var showCapDialog      by remember { mutableStateOf(false) }
    var showOverflowMenu   by remember { mutableStateOf(false) }
    var pendingPermRequest by remember { mutableStateOf<BrowserPermissionHandler.PermissionRequest?>(null) }
    var toastMessage       by remember { mutableStateOf("") }
    var toastIsEnable      by remember { mutableStateOf(true) }

    // Permission launcher (camera + mic)
    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val mediaReq = pendingPermRequest as? BrowserPermissionHandler.PermissionRequest.MediaRequest
        if (mediaReq != null) {
            val cam = grants[Manifest.permission.CAMERA] ?: false
            val mic = grants[Manifest.permission.RECORD_AUDIO] ?: false
            featureViewModel.grantMedia(mediaReq, cam || mic)
            pendingPermRequest = null
        }
    }

    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> featureViewModel.onFilesSelected(uris) }

    // Observe permission requests
    LaunchedEffect(Unit) {
        featureViewModel.permissionRequests.collect { req ->
            when (req) {
                is BrowserPermissionHandler.PermissionRequest.MediaRequest -> {
                    pendingPermRequest = req
                    val perms = buildList {
                        if (req.hasVideo) add(Manifest.permission.CAMERA)
                        if (req.hasAudio) add(Manifest.permission.RECORD_AUDIO)
                    }
                    mediaPermLauncher.launch(perms.toTypedArray())
                }
                else -> pendingPermRequest = req
            }
        }
    }

    // Observe file pick requests
    LaunchedEffect(Unit) {
        featureViewModel.filePickRequests.collect { req ->
            // OpenMultipleDocuments.launch() takes Array<String>
            filePicker.launch(req.mimeTypes)
        }
    }

    // Keep Alive events
    val kaEvent by tabViewModel.keepAliveEvent.collectAsState()
    LaunchedEffect(kaEvent) {
        when (val e = kaEvent) {
            is KeepAliveManager.KeepAliveEvent.CapExceeded -> { showCapDialog = true; tabViewModel.clearKeepAliveEvent() }
            is KeepAliveManager.KeepAliveEvent.Enabled     -> { toastMessage = "Keep Alive ON — ${e.tabTitle}"; toastIsEnable = true; tabViewModel.clearKeepAliveEvent() }
            is KeepAliveManager.KeepAliveEvent.Disabled    -> { toastMessage = "Keep Alive OFF — ${e.tabTitle}"; toastIsEnable = false; tabViewModel.clearKeepAliveEvent() }
            null -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {

        Column(Modifier.fillMaxSize()) {

            // ── Toolbar (hidden in fullscreen) ────────────────────────────
            if (!isFullscreen) {
                BrowserToolbar(
                    displayUrl     = url.ifEmpty { "duckduckgo.com" },
                    workspaceColor = wsColor,
                    tabCount       = tabState.tabs.size,
                    keepAliveCount = keepAliveCount,
                    isSecure       = isSecure,
                    isBookmarked   = isBookmarked,
                    loading        = loading,
                    canGoBack      = canGoBack,
                    canGoForward   = canGoFwd,
                    uaMode         = uaMode.name,
                    onNavigate     = { tabViewModel.loadUrl(it) },
                    onBack         = { tabViewModel.goBack() },
                    onForward      = { tabViewModel.goForward() },
                    onReload       = { tabViewModel.reload() },
                    onStop         = { tabViewModel.stop() },
                    onShowTabs     = { showTabOverview = true },
                    onShowKa       = { showKaPanel = true },
                    onBookmark     = { featureViewModel.toggleBookmark(url, title) },
                    onShowFind     = { featureViewModel.showFind() },
                    onToggleUa     = { session?.session?.let { featureViewModel.toggleUaMode(it) } },
                    onShowBookmarks = { featureViewModel.openBookmarks() },
                    showOverflowMenu = showOverflowMenu,
                    onToggleOverflow = { showOverflowMenu = !showOverflowMenu },
                    onDismissOverflow = { showOverflowMenu = false },
                )
            }

            // ── Tab Strip (hidden in fullscreen) ──────────────────────────
            if (!isFullscreen) {
                TabStrip(
                    tabs             = tabState.tabs,
                    activeTabId      = tabState.activeTab?.id,
                    workspaceColor   = wsColor,
                    onSelectTab      = { tabViewModel.selectTab(it) },
                    onCloseTab       = { tabViewModel.closeTab(it) },
                    onNewTab         = { tabViewModel.openNewTab() },
                    onPinTab         = { tab, pinned -> tabViewModel.setPinned(tab, pinned) },
                    onToggleKeepAlive = { tabViewModel.toggleKeepAlive(it) },
                )
            }

            // ── Progress bar ──────────────────────────────────────────────
            if (loading && !isFullscreen) {
                LinearProgressIndicator(
                    progress   = { progress / 100f },
                    modifier   = Modifier.fillMaxWidth(),
                    color      = parseWsColor(wsColor),
                    trackColor = Color.Transparent,
                )
            }

            // ── Web content ───────────────────────────────────────────────
            Box(Modifier.fillMaxSize()) {
                if (session != null) {
                    GeckoViewComposable(session = session.session, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF9C6FFF))
                    }
                }

                // Find-in-page bar (bottom of web view)
                AnimatedVisibility(
                    visible  = findVisible && !isFullscreen,
                    enter    = slideInVertically { it },
                    exit     = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    FindInPageBar(
                        result  = findResult,
                        onFind  = { q, fwd -> featureViewModel.find(q, fwd) },
                        onClose = { featureViewModel.hideFind() },
                    )
                }
            }
        }

        // ── Tab overview ──────────────────────────────────────────────────
        AnimatedVisibility(visible = showTabOverview,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut()) {
            TabOverviewScreen(
                tabs             = tabState.tabs,
                activeTabId      = tabState.activeTab?.id,
                workspaceColor   = wsColor,
                onSelectTab      = { tabViewModel.selectTab(it) },
                onCloseTab       = { tabViewModel.closeTab(it) },
                onNewTab         = { tabViewModel.openNewTab() },
                onPinTab         = { tab, pinned -> tabViewModel.setPinned(tab, pinned) },
                onToggleKeepAlive = { tabViewModel.toggleKeepAlive(it) },
                onCloseAll       = { tabViewModel.closeAllTabs() },
                onDismiss        = { showTabOverview = false },
                modifier         = Modifier.fillMaxSize(),
            )
        }

        // ── Keep Alive panel ──────────────────────────────────────────────
        AnimatedVisibility(visible = showKaPanel,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            KeepAlivePanel(
                keepAliveTabs      = tabViewModel.getKeepAliveTabs(),
                cap                = tabViewModel.keepAliveCap,
                onSelectTab        = { tabViewModel.selectTab(it) },
                onDisableKeepAlive = { tabViewModel.disableKeepAlive(it) },
                onDismiss          = { showKaPanel = false },
                modifier           = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            )
        }

        // ── Bookmarks panel ───────────────────────────────────────────────
        AnimatedVisibility(visible = showBookmarks,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            BookmarksPanel(
                bookmarks = bookmarks,
                onOpen    = { tabViewModel.loadUrl(it.url) },
                onDelete  = { featureViewModel.deleteBookmark(it) },
                onDismiss = { featureViewModel.closeBookmarks() },
                modifier  = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            )
        }

        // ── Permission dialog ─────────────────────────────────────────────
        pendingPermRequest?.let { req ->
            if (req !is BrowserPermissionHandler.PermissionRequest.MediaRequest) {
                PermissionDialog(
                    request = req,
                    onAllow = {
                        if (req is BrowserPermissionHandler.PermissionRequest.DownloadRequest)
                            featureViewModel.confirmDownload(req)
                        pendingPermRequest = null
                    },
                    onDeny  = { pendingPermRequest = null },
                )
            }
        }

        // ── Keep Alive cap dialog ─────────────────────────────────────────
        if (showCapDialog) {
            KeepAliveCapDialog(
                cap            = tabViewModel.keepAliveCap,
                onGoToSettings = { showCapDialog = false },
                onDismiss      = { showCapDialog = false },
            )
        }

        // ── Keep Alive toast ──────────────────────────────────────────────
        if (toastMessage.isNotEmpty()) {
            KeepAliveToast(
                message   = toastMessage,
                isEnable  = toastIsEnable,
                onDismiss = { toastMessage = "" },
                modifier  = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

// ── Toolbar ────────────────────────────────────────────────────────────────

@Composable
fun BrowserToolbar(
    displayUrl      : String,
    workspaceColor  : String,
    tabCount        : Int,
    keepAliveCount  : Int,
    isSecure        : Boolean,
    isBookmarked    : Boolean,
    loading         : Boolean,
    canGoBack       : Boolean,
    canGoForward    : Boolean,
    uaMode          : String,
    onNavigate      : (String) -> Unit,
    onBack          : () -> Unit,
    onForward       : () -> Unit,
    onReload        : () -> Unit,
    onStop          : () -> Unit,
    onShowTabs      : () -> Unit,
    onShowKa        : () -> Unit,
    onBookmark      : () -> Unit,
    onShowFind      : () -> Unit,
    onToggleUa      : () -> Unit,
    onShowBookmarks : () -> Unit,
    showOverflowMenu: Boolean,
    onToggleOverflow: () -> Unit,
    onDismissOverflow: () -> Unit,
) {
    var urlFieldValue by remember(displayUrl) { mutableStateOf(TextFieldValue(displayUrl)) }
    var isEditing     by remember { mutableStateOf(false) }
    val focusReq       = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current
    val wsColor        = parseWsColor(workspaceColor)

    Surface(tonalElevation = 2.dp, color = Color(0xFF1A1A1A)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 4.dp),
        ) {
            // Workspace accent bar
            Box(Modifier.width(3.dp).height(32.dp).background(wsColor, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))

            // Tab count
            FilledTonalButton(
                onClick = onShowTabs,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF252525), contentColor = Color(0xFFAAAAAA)),
                modifier = Modifier.height(30.dp),
            ) {
                Text("$tabCount", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.GridView, null, Modifier.size(14.dp))
            }
            Spacer(Modifier.width(2.dp))

            // Keep Alive bolt
            IconButton(onClick = onShowKa, modifier = Modifier.size(36.dp)) {
                if (keepAliveCount > 0) KeepAliveBolt(KeepAliveIndicatorSize.MEDIUM, animated = true)
                else Icon(Icons.Filled.Bolt, "KA", tint = Color(0xFF3A3A3A), modifier = Modifier.size(16.dp))
            }

            // Back / Fwd / Reload
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) Color.White else Color(0xFF3A3A3A), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowForward, "Fwd",
                    tint = if (canGoForward) Color.White else Color(0xFF3A3A3A), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = if (loading) onStop else onReload, modifier = Modifier.size(40.dp)) {
                Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    if (loading) "Stop" else "Reload", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // URL bar with security icon
            TextField(
                value           = if (isEditing) urlFieldValue else TextFieldValue(displayUrl),
                onValueChange   = { urlFieldValue = it },
                singleLine      = true,
                placeholder     = {
                    Text("Search or enter address", color = Color(0xFF4A4A4A), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = {
                    Icon(
                        if (isSecure) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        "Security",
                        tint     = if (isSecure) Color(0xFF81C784) else Color(0xFF888888),
                        modifier = Modifier.size(14.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    onNavigate(urlFieldValue.text); focusManager.clearFocus(); isEditing = false
                }),
                shape  = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF202020),
                    focusedTextColor        = Color.White,
                    unfocusedTextColor      = Color(0xFFCCCCCC),
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = wsColor,
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                modifier  = Modifier
                    .weight(1f).padding(end = 4.dp)
                    .focusRequester(focusReq)
                    .onFocusChanged { s -> isEditing = s.isFocused; if (s.isFocused) urlFieldValue = TextFieldValue(displayUrl) },
            )

            // Bookmark star
            IconButton(onClick = onBookmark, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarOutline,
                    "Bookmark",
                    tint     = if (isBookmarked) Color(0xFFFFB74D) else Color(0xFF888888),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Overflow menu
            Box {
                IconButton(onClick = onToggleOverflow, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.MoreVert, "More", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded         = showOverflowMenu,
                    onDismissRequest = onDismissOverflow,
                    modifier         = Modifier.background(Color(0xFF1E1E1E)),
                ) {
                    DropdownMenuItem(
                        text = { Text("Find in page", color = Color.White) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.White) },
                        onClick = { onDismissOverflow(); onShowFind() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (uaMode == "DESKTOP") "Switch to Mobile" else "Switch to Desktop",
                                color = Color.White,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (uaMode == "DESKTOP") Icons.Filled.PhoneAndroid else Icons.Filled.DesktopWindows,
                                null, tint = Color.White,
                            )
                        },
                        onClick = { onDismissOverflow(); onToggleUa() },
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks", color = Color.White) },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, null, tint = Color(0xFF9C6FFF)) },
                        onClick = { onDismissOverflow(); onShowBookmarks() },
                    )
                }
            }
        }
    }
}

// ── GeckoView composable ───────────────────────────────────────────────────

@Composable
fun GeckoViewComposable(session: GeckoSession, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            GeckoView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setSession(session)
            }
        },
        update  = { v -> if (v.session != session) { v.releaseSession(); v.setSession(session) } },
        modifier = modifier,
    )
}

// ── Utilities ──────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
fun <T> emptyStateFlow(default: T) =
    kotlinx.coroutines.flow.MutableStateFlow(default) as kotlinx.coroutines.flow.StateFlow<T>

fun parseWsColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF9C6FFF))

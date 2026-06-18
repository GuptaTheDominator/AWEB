@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
)

package com.aweb.browser.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
    val clipboard = LocalClipboardManager.current

    val tabState    by tabViewModel.uiState.collectAsState()
    val session     = tabState.activeSession
    val activeTab   = tabState.activeTab

    val emptyUrlFlow = remember { kotlinx.coroutines.flow.MutableStateFlow("") }
    val emptyTitleFlow = remember { kotlinx.coroutines.flow.MutableStateFlow("New Tab") }
    val emptyLoadingFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val emptyProgressFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
    val emptyBackFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val emptyForwardFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val emptySecureFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }

    val url         by (session?.url          ?: emptyUrlFlow).collectAsState()
    val title       by (session?.title        ?: emptyTitleFlow).collectAsState()
    val loading     by (session?.loading      ?: emptyLoadingFlow).collectAsState()
    val progress    by (session?.progress     ?: emptyProgressFlow).collectAsState()
    val canGoBack   by (session?.canGoBack    ?: emptyBackFlow).collectAsState()
    val canGoFwd    by (session?.canGoForward ?: emptyForwardFlow).collectAsState()
    val isSecure    by (session?.isSecure     ?: emptySecureFlow).collectAsState()

    val uaModeLabel = activeTab?.userAgentMode ?: "mobile"
    val wsColor     = activeWorkspace?.colorHex ?: "#9C6FFF"

    // ── Feature state ─────────────────────────────────────────────────────
    val isBookmarked  by featureViewModel.isBookmarked.collectAsState()
    val bookmarks     by featureViewModel.bookmarks.collectAsState()
    val findVisible   by featureViewModel.findVisible.collectAsState()
    val findResult    by featureViewModel.findResult.collectAsState()
    val isFullscreen  by featureViewModel.isFullscreen.collectAsState()
    val showBookmarks by featureViewModel.showBookmarks.collectAsState()
    val keepAliveCount = tabState.tabs.count { it.keepAlive }

    // Check bookmark status whenever URL changes
    LaunchedEffect(url) {
        try { if (url.isNotBlank()) featureViewModel.checkBookmark(url) }
        catch (e: Exception) { android.util.Log.w("BrowserScreen", "checkBookmark: ${e.message}") }
    }

    // FIX (Bug 3): Observe the inner session Flow for FindInPage attachment
    LaunchedEffect(session) {
        session?.sessionFlow?.collect { geckoSession ->
            if (geckoSession != null) {
                featureViewModel.attachFindSession(geckoSession)
            }
        }
    }

    // Fullscreen callback from GeckoView
    LaunchedEffect(session) {
        try {
            session?.onFullscreenChange = { fs ->
                try {
                    if (fs) activity?.let { featureViewModel.enterFullscreen(it) }
                    else    activity?.let { featureViewModel.exitFullscreen(it) }
                } catch (e: Exception) { android.util.Log.w("BrowserScreen", "fullscreen: ${e.message}") }
            }
        } catch (e: Exception) { android.util.Log.w("BrowserScreen", "fullscreenEffect: ${e.message}") }
    }

    // ── Overlay / dialog state ────────────────────────────────────────────
    var showTabOverview    by remember { mutableStateOf(false) }
    var showKaPanel        by remember { mutableStateOf(false) }
    var showCapDialog      by remember { mutableStateOf(false) }
    var showOverflowMenu   by remember { mutableStateOf(false) }
    var pendingPermRequest by remember { mutableStateOf<BrowserPermissionHandler.PermissionRequest?>(null) }
    var pendingLocationSystemRequest by remember { mutableStateOf<BrowserPermissionHandler.PermissionRequest.LocationRequest?>(null) }
    var pendingNotificationSystemRequest by remember { mutableStateOf<BrowserPermissionHandler.PermissionRequest.NotificationRequest?>(null) }
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
            featureViewModel.grantMedia(mediaReq, camGranted = cam, micGranted = mic)
            pendingPermRequest = null
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingLocationSystemRequest
        if (req != null) {
            val granted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
            if (granted) pendingPermRequest = req else req.callback.reject()
            pendingLocationSystemRequest = null
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val req = pendingNotificationSystemRequest
        if (req != null) {
            if (granted) pendingPermRequest = req else req.callback.reject()
            pendingNotificationSystemRequest = null
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
                is BrowserPermissionHandler.PermissionRequest.LocationRequest -> {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) {
                        pendingPermRequest = req
                    } else {
                        pendingLocationSystemRequest = req
                        locationPermLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ))
                    }
                }
                is BrowserPermissionHandler.PermissionRequest.NotificationRequest -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingPermRequest = req
                    } else {
                        pendingNotificationSystemRequest = req
                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
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
                    uaMode         = uaModeLabel,
                    onNavigate     = { tabViewModel.loadUrl(it) },
                    onBack         = { tabViewModel.goBack() },
                    onForward      = { tabViewModel.goForward() },
                    onReload       = { tabViewModel.reload() },
                    onStop         = { tabViewModel.stop() },
                    onShowTabs     = { showTabOverview = true },
                    onShowKa       = { showKaPanel = true },
                    onBookmark     = { featureViewModel.toggleBookmark(url, title) },
                    onShowFind     = { featureViewModel.showFind() },
                    onToggleUa     = { tabViewModel.toggleDesktopMode() },
                    onCopyUrl       = {
                        val toCopy = url.ifBlank { activeTab?.url.orEmpty() }
                        if (toCopy.isNotBlank() && !toCopy.startsWith("about:")) {
                            clipboard.setText(AnnotatedString(toCopy))
                            toastMessage = "URL copied"
                            toastIsEnable = true
                        }
                    },
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
                    /*
                     * FIX (Bug 3): session.session (the inner GeckoSession) is null
                     * until open() completes on the Main thread. Instead of showing a
                     * blank black box, we show a progress spinner — then when the session
                     * object becomes non-null Compose will recompose and show GeckoView.
                     *
                     * GeckoSessionWrapper._session is @Volatile so reads are always fresh.
                     * Compose reads session.session here on every recompose, which is
                     * triggered naturally by state changes (loading, url, etc.).
                     */
                    val geckoSession = session.session
                    if (geckoSession != null) {
                        GeckoViewComposable(session = geckoSession, modifier = Modifier.fillMaxSize())
                    } else {
                        // Session opened but GeckoSession object not yet created — spin briefly
                        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F)),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF9C6FFF), modifier = Modifier.size(32.dp))
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF9C6FFF))
                    }
                }

                // Find-in-page bar — shown at the bottom of the web view area
                if (findVisible && !isFullscreen) {
                    FindInPageBar(
                        result   = findResult,
                        onFind   = { q, fwd -> featureViewModel.find(q, fwd) },
                        onClose  = { featureViewModel.hideFind() },
                        modifier = Modifier.align(Alignment.BottomCenter),
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
                        when (req) {
                            is BrowserPermissionHandler.PermissionRequest.DownloadRequest ->
                                featureViewModel.confirmDownload(req)
                            is BrowserPermissionHandler.PermissionRequest.LocationRequest ->
                                req.callback.grant()   // resolves the GeckoResult<Int>
                            is BrowserPermissionHandler.PermissionRequest.NotificationRequest ->
                                req.callback.grant()   // resolves the GeckoResult<Int>
                            else -> Unit
                        }
                        pendingPermRequest = null
                    },
                    onDeny  = {
                        when (req) {
                            is BrowserPermissionHandler.PermissionRequest.LocationRequest ->
                                req.callback.reject()
                            is BrowserPermissionHandler.PermissionRequest.NotificationRequest ->
                                req.callback.reject()
                            else -> Unit
                        }
                        pendingPermRequest = null
                    },
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
    onCopyUrl       : () -> Unit,
    onShowBookmarks : () -> Unit,
    showOverflowMenu: Boolean,
    onToggleOverflow: () -> Unit,
    onDismissOverflow: () -> Unit,
) {
    /*
     * FIX (Bug 9): Using remember(displayUrl) reset urlFieldValue on every URL change
     * (e.g. on page navigation), wiping what the user was typing mid-edit.
     *
     * Fix: decouple the edit buffer from displayUrl.
     * - urlFieldValue only changes when user types (onValueChange).
     * - When the user is NOT editing, the TextField shows displayUrl directly.
     * - When the user focuses the field, we seed urlFieldValue from displayUrl ONCE
     *   (via onFocusChanged), giving them the current URL to edit from.
     */
    var urlFieldValue by remember { mutableStateOf(TextFieldValue(displayUrl)) }
    var isEditing     by remember { mutableStateOf(false) }
    // Keep urlFieldValue in sync with page navigations when NOT editing
    LaunchedEffect(displayUrl) {
        if (!isEditing) urlFieldValue = TextFieldValue(displayUrl)
    }
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
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) Color.White else Color(0xFF444444), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Fwd",
                    tint = if (canGoForward) Color.White else Color(0xFF444444), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = if (loading) onStop else onReload, modifier = Modifier.size(44.dp)) {
                Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    if (loading) "Stop" else "Reload", tint = Color.White, modifier = Modifier.size(22.dp))
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
                        if (isSecure) "Secure connection" else "Not secure or local page",
                        tint     = if (isSecure) Color(0xFF81C784) else Color(0xFF888888),
                        modifier = Modifier.size(14.dp),
                    )
                },
                trailingIcon = {
                    if (isEditing && urlFieldValue.text.isNotBlank()) {
                        IconButton(
                            onClick = { urlFieldValue = TextFieldValue("") },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                "Clear address",
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    onNavigate(urlFieldValue.text); focusManager.clearFocus(); isEditing = false
                }),
                shape  = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color(0xFF2D2D2D),
                    unfocusedContainerColor = Color(0xFF242424),
                    focusedTextColor        = Color.White,
                    unfocusedTextColor      = Color(0xFFBBBBBB),
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = wsColor,
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier  = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .padding(vertical = 6.dp)
                    .focusRequester(focusReq)
                    .onFocusChanged { s ->
                        if (s.isFocused && !isEditing) {
                            // Seed edit buffer with current URL when user taps the bar
                            urlFieldValue = TextFieldValue(
                                text = displayUrl,
                                selection = androidx.compose.ui.text.TextRange(0, displayUrl.length),
                            )
                        }
                        isEditing = s.isFocused
                    },
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
                                if (uaMode.equals("desktop", ignoreCase = true)) "Switch to Mobile" else "Switch to Desktop",
                                color = Color.White,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (uaMode.equals("desktop", ignoreCase = true)) Icons.Filled.PhoneAndroid else Icons.Filled.DesktopWindows,
                                null, tint = Color.White,
                            )
                        },
                        onClick = { onDismissOverflow(); onToggleUa() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy current URL", color = Color.White) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = Color.White) },
                        onClick = { onDismissOverflow(); onCopyUrl() },
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
            try {
                GeckoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    try { setSession(session) }
                    catch (e: Exception) {
                        android.util.Log.e("GeckoViewComposable", "setSession factory: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GeckoViewComposable", "GeckoView creation: ${e.message}")
                android.widget.FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            }
        },
        update = { v ->
            if (v is GeckoView) {
                try {
                    if (v.session !== session) {
                        try { v.releaseSession() } catch (_: Exception) {}
                        try { v.setSession(session) }
                        catch (e: Exception) { android.util.Log.e("GeckoViewComposable", "update: ${e.message}") }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeckoViewComposable", "update outer: ${e.message}")
                }
            }
        },
        modifier = modifier,
    )
}

// ── Utilities ──────────────────────────────────────────────────────────────

fun parseWsColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF9C6FFF))

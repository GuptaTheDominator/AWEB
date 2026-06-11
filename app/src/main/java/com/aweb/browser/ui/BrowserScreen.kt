package com.aweb.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.ui.keepalive.*
import com.aweb.browser.ui.tabs.TabOverviewScreen
import com.aweb.browser.ui.tabs.TabStrip
import com.aweb.browser.ui.tabs.TabViewModel
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Phase 5 BrowserScreen.
 *
 * Additions over Phase 4:
 *  - Keep Alive bolt button in toolbar (amber, animated when KA tabs > 0)
 *  - Keep Alive panel (bottom sheet overlay) with cap bar and tab list
 *  - KeepAliveCapDialog when user tries to exceed the cap
 *  - KeepAliveToast confirming enable / disable
 *  - All tab strip / overview calls now route through toggleKeepAlive()
 */
@Composable
fun BrowserScreen(
    tabViewModel    : TabViewModel,
    activeWorkspace : WorkspaceEntity?,
) {
    val tabState by tabViewModel.uiState.collectAsState()
    val session  = tabState.activeSession

    val url       by (session?.url        ?: emptyStateFlow("")).collectAsState()
    val loading   by (session?.loading    ?: emptyStateFlow(false)).collectAsState()
    val progress  by (session?.progress   ?: emptyStateFlow(0)).collectAsState()
    val canGoBack by (session?.canGoBack  ?: emptyStateFlow(false)).collectAsState()
    val canGoFwd  by (session?.canGoForward ?: emptyStateFlow(false)).collectAsState()

    val wsColor = activeWorkspace?.colorHex ?: "#9C6FFF"

    // ── Local UI state ────────────────────────────────────────────────────
    var showTabOverview  by remember { mutableStateOf(false) }
    var showKeepAlivePanel by remember { mutableStateOf(false) }
    var showCapDialog    by remember { mutableStateOf(false) }
    var toastMessage     by remember { mutableStateOf("") }
    var toastIsEnable    by remember { mutableStateOf(true) }

    // Observe Keep Alive events from ViewModel
    val kaEvent by tabViewModel.keepAliveEvent.collectAsState()
    LaunchedEffect(kaEvent) {
        when (val e = kaEvent) {
            is KeepAliveManager.KeepAliveEvent.CapExceeded -> {
                showCapDialog = true
                tabViewModel.clearKeepAliveEvent()
            }
            is KeepAliveManager.KeepAliveEvent.Enabled -> {
                toastMessage  = "Keep Alive ON — ${e.tabTitle}"
                toastIsEnable = true
                tabViewModel.clearKeepAliveEvent()
            }
            is KeepAliveManager.KeepAliveEvent.Disabled -> {
                toastMessage  = "Keep Alive OFF — ${e.tabTitle}"
                toastIsEnable = false
                tabViewModel.clearKeepAliveEvent()
            }
            null -> Unit
        }
    }

    val keepAliveCount = tabState.tabs.count { it.keepAlive }
    val keepAliveCap   = tabViewModel.keepAliveCap

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Toolbar ───────────────────────────────────────────────────
            BrowserToolbar(
                displayUrl     = url.ifEmpty { "duckduckgo.com" },
                workspaceColor = wsColor,
                tabCount       = tabState.tabs.size,
                keepAliveCount = keepAliveCount,
                loading        = loading,
                canGoBack      = canGoBack,
                canGoForward   = canGoFwd,
                onNavigate     = { tabViewModel.loadUrl(it) },
                onBack         = { tabViewModel.goBack() },
                onForward      = { tabViewModel.goForward() },
                onReload       = { tabViewModel.reload() },
                onStop         = { tabViewModel.stop() },
                onShowTabs     = { showTabOverview = true },
                onShowKeepAlive = { showKeepAlivePanel = true },
            )

            // ── Tab Strip ─────────────────────────────────────────────────
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

            // ── Progress bar ──────────────────────────────────────────────
            if (loading) {
                LinearProgressIndicator(
                    progress   = { progress / 100f },
                    modifier   = Modifier.fillMaxWidth(),
                    color      = runCatching {
                        Color(android.graphics.Color.parseColor(wsColor))
                    }.getOrDefault(Color(0xFF9C6FFF)),
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
            }
        }

        // ── Tab overview overlay ──────────────────────────────────────────
        AnimatedVisibility(
            visible = showTabOverview,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut(),
        ) {
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

        // ── Keep Alive panel (bottom sheet style) ─────────────────────────
        AnimatedVisibility(
            visible = showKeepAlivePanel,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            KeepAlivePanel(
                keepAliveTabs      = tabViewModel.getKeepAliveTabs(),
                cap                = keepAliveCap,
                onSelectTab        = { tabViewModel.selectTab(it) },
                onDisableKeepAlive = { tabViewModel.disableKeepAlive(it) },
                onDismiss          = { showKeepAlivePanel = false },
                modifier           = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            )
        }

        // ── Keep Alive cap exceeded dialog ────────────────────────────────
        if (showCapDialog) {
            KeepAliveCapDialog(
                cap            = keepAliveCap,
                onGoToSettings = { showCapDialog = false /* TODO: nav to settings */ },
                onDismiss      = { showCapDialog = false },
            )
        }

        // ── Keep Alive toast ──────────────────────────────────────────────
        if (toastMessage.isNotEmpty()) {
            KeepAliveToast(
                message   = toastMessage,
                isEnable  = toastIsEnable,
                onDismiss = { toastMessage = "" },
                modifier  = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
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
    loading         : Boolean,
    canGoBack       : Boolean,
    canGoForward    : Boolean,
    onNavigate      : (String) -> Unit,
    onBack          : () -> Unit,
    onForward       : () -> Unit,
    onReload        : () -> Unit,
    onStop          : () -> Unit,
    onShowTabs      : () -> Unit,
    onShowKeepAlive : () -> Unit,
) {
    var urlFieldValue by remember(displayUrl) { mutableStateOf(TextFieldValue(displayUrl)) }
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspaceColor))
    }.getOrDefault(Color(0xFF9C6FFF))

    Surface(tonalElevation = 2.dp, color = Color(0xFF1A1A1A)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 4.dp),
        ) {
            // Workspace colour accent
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(wsColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(4.dp))

            // Tab count button
            FilledTonalButton(
                onClick        = onShowTabs,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors         = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF252525),
                    contentColor   = Color(0xFFAAAAAA),
                ),
                modifier = Modifier.height(30.dp),
            ) {
                Text("$tabCount", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.GridView, null, Modifier.size(14.dp))
            }

            Spacer(Modifier.width(2.dp))

            // Keep Alive bolt button — amber when tabs are alive, grey when none
            IconButton(
                onClick  = onShowKeepAlive,
                modifier = Modifier.size(36.dp),
            ) {
                if (keepAliveCount > 0) {
                    KeepAliveBolt(
                        size     = KeepAliveIndicatorSize.MEDIUM,
                        animated = true,
                    )
                } else {
                    Icon(
                        Icons.Filled.Bolt, "Keep Alive",
                        tint     = Color(0xFF444444),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.width(2.dp))

            // Back / Forward / Reload-Stop
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.ArrowBack, "Back",
                    tint     = if (canGoBack) Color.White else Color(0xFF3A3A3A),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.ArrowForward, "Forward",
                    tint     = if (canGoForward) Color.White else Color(0xFF3A3A3A),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick  = if (loading) onStop else onReload,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    if (loading) "Stop" else "Reload",
                    tint     = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            // URL bar
            TextField(
                value           = if (isEditing) urlFieldValue else TextFieldValue(displayUrl),
                onValueChange   = { urlFieldValue = it },
                singleLine      = true,
                placeholder     = {
                    Text(
                        "Search or enter address",
                        color    = Color(0xFF4A4A4A),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    onNavigate(urlFieldValue.text)
                    focusManager.clearFocus()
                    isEditing = false
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
                    .weight(1f)
                    .padding(end = 4.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { s ->
                        isEditing = s.isFocused
                        if (s.isFocused) urlFieldValue = TextFieldValue(displayUrl)
                    },
            )
        }
    }
}

// ── GeckoView composable ───────────────────────────────────────────────────

@Composable
fun GeckoViewComposable(
    session  : GeckoSession,
    modifier : Modifier = Modifier,
) {
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
        update = { v ->
            if (v.session != session) { v.releaseSession(); v.setSession(session) }
        },
        modifier = modifier,
    )
}

// ── Utilities ──────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun <T> emptyStateFlow(default: T) =
    kotlinx.coroutines.flow.MutableStateFlow(default) as kotlinx.coroutines.flow.StateFlow<T>

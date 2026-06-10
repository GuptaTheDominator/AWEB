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
import com.aweb.browser.ui.tabs.TabOverviewScreen
import com.aweb.browser.ui.tabs.TabStrip
import com.aweb.browser.ui.tabs.TabViewModel
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Phase 3 BrowserScreen — workspace-aware + full tab management.
 *
 * Layout:
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  [Toolbar: back/fwd/reload | tab count | url bar]        │
 *  │  [Tab Strip: scrollable tabs + new tab button]           │
 *  │  [Progress bar if loading]                               │
 *  │  [GeckoView — active tab of active workspace]            │
 *  └──────────────────────────────────────────────────────────┘
 *
 * Tab Overview is shown as a full-pane overlay when the tab count is tapped.
 */
@Composable
fun BrowserScreen(
    tabViewModel      : TabViewModel,
    activeWorkspace   : WorkspaceEntity?,
) {
    val tabState   by tabViewModel.uiState.collectAsState()
    val session    = tabState.activeSession

    val url        by (session?.url        ?: emptyStateFlow("")).collectAsState()
    val loading    by (session?.loading    ?: emptyStateFlow(false)).collectAsState()
    val progress   by (session?.progress   ?: emptyStateFlow(0)).collectAsState()
    val canGoBack  by (session?.canGoBack  ?: emptyStateFlow(false)).collectAsState()
    val canGoFwd   by (session?.canGoForward ?: emptyStateFlow(false)).collectAsState()

    val wsColor = activeWorkspace?.colorHex ?: "#9C6FFF"

    var showTabOverview by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Toolbar ───────────────────────────────────────────────────
            BrowserToolbar(
                displayUrl     = url.ifEmpty { "duckduckgo.com" },
                workspaceName  = activeWorkspace?.name ?: "",
                workspaceColor = wsColor,
                tabCount       = tabState.tabs.size,
                loading        = loading,
                canGoBack      = canGoBack,
                canGoForward   = canGoFwd,
                onNavigate     = { tabViewModel.loadUrl(it) },
                onBack         = { tabViewModel.goBack() },
                onForward      = { tabViewModel.goForward() },
                onReload       = { tabViewModel.reload() },
                onStop         = { tabViewModel.stop() },
                onShowTabs     = { showTabOverview = true },
            )

            // ── Tab Strip ─────────────────────────────────────────────────
            TabStrip(
                tabs           = tabState.tabs,
                activeTabId    = tabState.activeTab?.id,
                workspaceColor = wsColor,
                onSelectTab    = { tabViewModel.selectTab(it) },
                onCloseTab     = { tabViewModel.closeTab(it) },
                onNewTab       = { tabViewModel.openNewTab() },
                onPinTab       = { tab, pinned -> tabViewModel.setPinned(tab, pinned) },
                onKeepAlive    = { tab, ka -> tabViewModel.setKeepAlive(tab, ka) },
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
            Box(modifier = Modifier.fillMaxSize()) {
                if (session != null) {
                    GeckoViewComposable(
                        session  = session.session,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(android.graphics.Color.parseColor(wsColor).let {
                                Color(it)
                            }),
                        )
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
                tabs           = tabState.tabs,
                activeTabId    = tabState.activeTab?.id,
                workspaceColor = wsColor,
                onSelectTab    = { tabViewModel.selectTab(it) },
                onCloseTab     = { tabViewModel.closeTab(it) },
                onNewTab       = { tabViewModel.openNewTab() },
                onPinTab       = { tab, pinned -> tabViewModel.setPinned(tab, pinned) },
                onKeepAlive    = { tab, ka -> tabViewModel.setKeepAlive(tab, ka) },
                onCloseAll     = { tabViewModel.closeAllTabs() },
                onDismiss      = { showTabOverview = false },
                modifier       = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Toolbar ────────────────────────────────────────────────────────────────

@Composable
fun BrowserToolbar(
    displayUrl     : String,
    workspaceName  : String,
    workspaceColor : String,
    tabCount       : Int,
    loading        : Boolean,
    canGoBack      : Boolean,
    canGoForward   : Boolean,
    onNavigate     : (String) -> Unit,
    onBack         : () -> Unit,
    onForward      : () -> Unit,
    onReload       : () -> Unit,
    onStop         : () -> Unit,
    onShowTabs     : () -> Unit,
) {
    var urlFieldValue by remember(displayUrl) { mutableStateOf(TextFieldValue(displayUrl)) }
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspaceColor))
    }.getOrDefault(Color(0xFF9C6FFF))

    Surface(
        tonalElevation = 2.dp,
        color          = Color(0xFF1A1A1A),
    ) {
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
                onClick = onShowTabs,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
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

            // Back / Forward / Reload
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) Color.White else Color(0xFF444444),
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowForward, "Forward",
                    tint = if (canGoForward) Color.White else Color(0xFF444444),
                    modifier = Modifier.size(20.dp))
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
                    Text("Search or enter address", color = Color(0xFF555555), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    .onFocusChanged { state ->
                        isEditing = state.isFocused
                        if (state.isFocused) urlFieldValue = TextFieldValue(displayUrl)
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
        update = { geckoView ->
            if (geckoView.session != session) {
                geckoView.releaseSession()
                geckoView.setSession(session)
            }
        },
        modifier = modifier,
    )
}

// ── Utilities ──────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun <T> emptyStateFlow(default: T) =
    kotlinx.coroutines.flow.MutableStateFlow(default) as kotlinx.coroutines.flow.StateFlow<T>

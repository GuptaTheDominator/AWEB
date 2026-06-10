package com.aweb.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.aweb.browser.ui.workspace.WorkspaceViewModel
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Phase 2 BrowserScreen — workspace-aware.
 *
 * Now receives state from [WorkspaceViewModel] instead of the old single-session
 * BrowserViewModel. The active GeckoSession belongs to the active workspace.
 *
 * Layout (tablet):
 *  ┌────────────────────────────────────────────────────────────┐
 *  │  [WorkspaceSidebar 220dp] │ [Toolbar] [ProgressBar]       │
 *  │                           │ [GeckoView - active workspace] │
 *  └────────────────────────────────────────────────────────────┘
 *
 * The sidebar is rendered by MainActivity which owns the full scaffold.
 * BrowserScreen only renders the browser pane (toolbar + web content).
 */
@Composable
fun BrowserScreen(
    viewModel : WorkspaceViewModel,
) {
    val uiState    by viewModel.uiState.collectAsState()
    val session    = uiState.activeSession

    // Collect browser state flows from the active session
    val url        by (session?.url        ?: emptyFlow()).collectAsState(initial = "")
    val loading    by (session?.loading    ?: emptyFlow()).collectAsState(initial = false)
    val progress   by (session?.progress   ?: emptyFlow()).collectAsState(initial = 0)
    val canGoBack  by (session?.canGoBack  ?: emptyFlow()).collectAsState(initial = false)
    val canGoFwd   by (session?.canGoForward ?: emptyFlow()).collectAsState(initial = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        BrowserToolbar(
            displayUrl   = url.ifEmpty { "duckduckgo.com" },
            workspaceName = uiState.activeWorkspace?.name ?: "",
            workspaceColor = uiState.activeWorkspace?.colorHex ?: "#9C6FFF",
            loading      = loading,
            canGoBack    = canGoBack,
            canGoForward = canGoFwd,
            onNavigate   = { viewModel.loadUrl(it) },
            onBack       = { viewModel.goBack() },
            onForward    = { viewModel.goForward() },
            onReload     = { viewModel.reload() },
            onStop       = { viewModel.stop() },
        )

        if (loading) {
            LinearProgressIndicator(
                progress   = { progress / 100f },
                modifier   = Modifier.fillMaxWidth(),
                color      = Color(0xFF9C6FFF),
                trackColor = Color.Transparent,
            )
        }

        // Render active workspace's GeckoSession
        if (session != null) {
            GeckoViewComposable(
                session  = session.session,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Loading / no workspace state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF9C6FFF))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun <T> emptyFlow() = kotlinx.coroutines.flow.MutableStateFlow<T?>(null) as kotlinx.coroutines.flow.StateFlow<T>

// ── Toolbar ────────────────────────────────────────────────────────────────

@Composable
fun BrowserToolbar(
    displayUrl     : String,
    workspaceName  : String,
    workspaceColor : String,
    loading        : Boolean,
    canGoBack      : Boolean,
    canGoForward   : Boolean,
    onNavigate     : (String) -> Unit,
    onBack         : () -> Unit,
    onForward      : () -> Unit,
    onReload       : () -> Unit,
    onStop         : () -> Unit,
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
            // Workspace colour indicator bar on the left edge of toolbar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(wsColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(4.dp))

            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) Color.White else Color(0xFF555555))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.Filled.ArrowForward, "Forward",
                    tint = if (canGoForward) Color.White else Color(0xFF555555))
            }
            IconButton(onClick = if (loading) onStop else onReload) {
                Icon(
                    if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    if (loading) "Stop" else "Reload",
                    tint = Color.White,
                )
            }

            // URL bar
            TextField(
                value           = if (isEditing) urlFieldValue else TextFieldValue(displayUrl),
                onValueChange   = { urlFieldValue = it },
                singleLine      = true,
                placeholder     = {
                    Text("Search or enter address", color = Color(0xFF666666), fontSize = 14.sp,
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
                    focusedContainerColor   = Color(0xFF272727),
                    unfocusedContainerColor = Color(0xFF222222),
                    focusedTextColor        = Color.White,
                    unfocusedTextColor      = Color(0xFFCCCCCC),
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = wsColor,
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier  = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        isEditing = state.isFocused
                        if (state.isFocused) urlFieldValue = TextFieldValue(displayUrl)
                    }
            )
        }
    }
}

// ── GeckoView composable interop ───────────────────────────────────────────

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

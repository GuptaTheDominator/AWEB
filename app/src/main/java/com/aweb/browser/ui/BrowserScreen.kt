package com.aweb.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.hilt.navigation.compose.hiltViewModel
import org.mozilla.geckoview.GeckoView

/**
 * Phase 1 — single-tab browser screen.
 *
 * Layout:
 *  ┌─────────────────────────────────────────────────────┐
 *  │  [◀] [▶] [↺/✕]  [  URL bar / search  ]            │  ← top toolbar
 *  │  ━━━━━━━━━━━━━━━ progress bar ━━━━━━━━━━━━━━━━━━━━  │
 *  │                                                       │
 *  │                   GeckoView                          │
 *  │                                                       │
 *  └─────────────────────────────────────────────────────┘
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val url         by viewModel.url.collectAsState()
    val title       by viewModel.title.collectAsState()
    val progress    by viewModel.progress.collectAsState()
    val loading     by viewModel.loading.collectAsState()
    val canGoBack   by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()

    Scaffold(
        topBar = {
            BrowserToolbar(
                displayUrl     = url.ifEmpty { "duckduckgo.com" },
                loading        = loading,
                canGoBack      = canGoBack,
                canGoForward   = canGoForward,
                onNavigate     = { viewModel.loadUrl(it) },
                onBack         = { viewModel.goBack() },
                onForward      = { viewModel.goForward() },
                onReload       = { viewModel.reload() },
                onStop         = { viewModel.stop() },
            )
        },
        containerColor = Color(0xFF121212),
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading progress bar
            if (loading) {
                LinearProgressIndicator(
                    progress   = { progress / 100f },
                    modifier   = Modifier.fillMaxWidth(),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }

            // GeckoView — wrapped in AndroidView for Compose interop
            GeckoViewComposable(
                session  = viewModel.session,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}

// ── Toolbar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserToolbar(
    displayUrl   : String,
    loading      : Boolean,
    canGoBack    : Boolean,
    canGoForward : Boolean,
    onNavigate   : (String) -> Unit,
    onBack       : () -> Unit,
    onForward    : () -> Unit,
    onReload     : () -> Unit,
    onStop       : () -> Unit,
) {
    var urlFieldValue by remember(displayUrl) {
        mutableStateOf(TextFieldValue(displayUrl))
    }
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    Surface(
        tonalElevation = 2.dp,
        color          = Color(0xFF1E1E1E),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
        ) {
            // Back
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) Color.White else Color.Gray
                )
            }

            // Forward
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) Color.White else Color.Gray
                )
            }

            // Reload / Stop
            IconButton(onClick = if (loading) onStop else onReload) {
                Icon(
                    if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (loading) "Stop" else "Reload",
                    tint = Color.White
                )
            }

            // URL bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                TextField(
                    value           = if (isEditing) urlFieldValue
                                      else TextFieldValue(displayUrl),
                    onValueChange   = { urlFieldValue = it },
                    singleLine      = true,
                    placeholder     = {
                        Text(
                            "Search or enter address",
                            color    = Color.Gray,
                            fontSize = 14.sp,
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
                        unfocusedContainerColor = Color(0xFF2A2A2A),
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.LightGray,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor             = MaterialTheme.colorScheme.primary,
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier  = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            isEditing = state.isFocused
                            if (state.isFocused) {
                                urlFieldValue = TextFieldValue(displayUrl)
                            }
                        }
                )
            }
        }
    }
}

// ── GeckoView composable interop ───────────────────────────────────────────

@Composable
fun GeckoViewComposable(
    session  : org.mozilla.geckoview.GeckoSession,
    modifier : Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            GeckoView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setSession(session)
            }
        },
        update = { geckoView ->
            // If the session changes (Phase 3+), re-attach it here
            if (geckoView.session != session) {
                geckoView.setSession(session)
            }
        },
        modifier = modifier,
    )
}

package com.aweb.browser.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.AppState
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.gecko.GeckoSessionWrapper
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.ui.keepalive.KeepAliveManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TabUiState(
    val tabs          : List<TabEntity>      = emptyList(),
    val activeTab     : TabEntity?           = null,
    val activeSession : GeckoSessionWrapper? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TabViewModel @Inject constructor(
    private val tabRepo         : TabRepository,
    private val sessionManager  : TabSessionManager,
    private val lifecycleManager: TabLifecycleManager,
    private val keepAliveManager: KeepAliveManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TabUiState())
    val uiState: StateFlow<TabUiState> = _uiState.asStateFlow()

    /** Expose Keep Alive events for BrowserScreen to react to (toast / dialog). */
    val keepAliveEvent = keepAliveManager.event

    private val _activeWorkspace = MutableStateFlow<WorkspaceEntity?>(null)

    // Browser state shortcuts
    val url          get() = _uiState.value.activeSession?.url
    val title        get() = _uiState.value.activeSession?.title
    val progress     get() = _uiState.value.activeSession?.progress
    val loading      get() = _uiState.value.activeSession?.loading
    val canGoBack    get() = _uiState.value.activeSession?.canGoBack
    val canGoForward get() = _uiState.value.activeSession?.canGoForward

    // Keep Alive cap (for toolbar badge and panel)
    val keepAliveCount  get() = _uiState.value.tabs.count { it.keepAlive }
    val keepAliveCap    get() = lifecycleManager.policy.maxKeepAlive

    init {
        viewModelScope.launch {
            _activeWorkspace
                .filterNotNull()
                .flatMapLatest { ws ->
                    tabRepo.observeTabsForWorkspace(ws.id).map { tabs -> ws to tabs }
                }
                .collect { (ws, rawTabs) ->

                    // Seed homepage tab if workspace empty
                    val tabs = if (rawTabs.isEmpty()) {
                        val t = tabRepo.createTab(workspaceId = ws.id)
                        tabRepo.setActiveTab(ws.id, t.id)
                        listOf(t.copy(isActive = true))
                    } else rawTabs

                    val prev   = _uiState.value.activeTab
                    val active = tabs.firstOrNull { it.isActive } ?: tabs.first()

                    val isWorkspaceSwitch =
                        prev == null || prev.workspaceId != active.workspaceId

                    if (isWorkspaceSwitch) {
                        lifecycleManager.onAppRestore(tabs, ws)
                    }

                    val session = sessionManager.getOrCreate(active, ws)
                    wireSessionCallbacks(session, active)
                    AppState.update(ws, tabs)

                    _uiState.update {
                        it.copy(tabs = tabs, activeTab = active, activeSession = session)
                    }
                }
        }
    }

    fun setWorkspace(workspace: WorkspaceEntity) {
        _activeWorkspace.value = workspace
    }

    // ── Tab actions ───────────────────────────────────────────────────────

    fun openNewTab(url: String = "https://duckduckgo.com") {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            val tab = tabRepo.createTab(workspaceId = ws.id, url = url)
            tabRepo.setActiveTab(ws.id, tab.id)
        }
    }

    fun selectTab(tab: TabEntity) {
        val ws  = _activeWorkspace.value ?: return
        val prev = _uiState.value.activeTab
        val all  = _uiState.value.tabs
        viewModelScope.launch {
            tabRepo.setActiveTab(ws.id, tab.id)
            lifecycleManager.onTabSelected(
                newActiveTab      = tab,
                previousActiveTab = prev,
                allTabs           = all,
                workspace         = ws,
            )
        }
    }

    fun closeTab(tab: TabEntity) {
        val ws  = _activeWorkspace.value ?: return
        val all = _uiState.value.tabs
        viewModelScope.launch {
            val wasActive = tab.id == _uiState.value.activeTab?.id
            tabRepo.closeTab(tab.id)
            lifecycleManager.onTabClosed(tab, all, ws)
            if (wasActive) {
                val remaining = tabRepo.getTabsForWorkspace(ws.id)
                val next = remaining.firstOrNull()
                if (next != null) tabRepo.setActiveTab(ws.id, next.id)
                else tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
            }
        }
    }

    fun closeAllTabs() {
        val ws   = _activeWorkspace.value ?: return
        val tabs = _uiState.value.tabs
        viewModelScope.launch {
            tabs.forEach { lifecycleManager.onTabClosed(it, tabs, ws) }
            tabRepo.closeAllTabsInWorkspace(ws.id)
            tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
        }
    }

    fun setPinned(tab: TabEntity, pinned: Boolean) {
        viewModelScope.launch { tabRepo.setPinned(tab.id, pinned) }
    }

    // ── Keep Alive actions ────────────────────────────────────────────────

    /**
     * Primary Keep Alive toggle — routes through [KeepAliveManager] which
     * enforces the cap, updates Room, rebalances sessions, and emits events.
     */
    fun toggleKeepAlive(tab: TabEntity) {
        val ws  = _activeWorkspace.value ?: return
        val all = _uiState.value.tabs
        keepAliveManager.toggle(tab, all, ws)
    }

    /** Disable Keep Alive on a specific tab (from panel "disable" button). */
    fun disableKeepAlive(tab: TabEntity) {
        if (!tab.keepAlive) return
        toggleKeepAlive(tab)
    }

    fun clearKeepAliveEvent() = keepAliveManager.clearEvent()

    /** All Keep Alive tabs in the active workspace, sorted by last accessed. */
    fun getKeepAliveTabs(): List<TabEntity> =
        keepAliveManager.getKeepAliveTabs(_uiState.value.tabs)

    fun reorderTabs(orderedIds: List<String>) {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch { tabRepo.reorder(ws.id, orderedIds) }
    }

    // ── Browser passthrough ───────────────────────────────────────────────

    fun loadUrl(input: String) {
        val session = _uiState.value.activeSession ?: return
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ")                  -> "https://$input"
            else -> "https://duckduckgo.com/?q=${input.trim().replace(" ", "+")}"
        }
        session.loadUrl(url)
    }

    fun goBack()    = _uiState.value.activeSession?.goBack()
    fun goForward() = _uiState.value.activeSession?.goForward()
    fun reload()    = _uiState.value.activeSession?.reload()
    fun stop()      = _uiState.value.activeSession?.stopLoading()

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun wireSessionCallbacks(session: GeckoSessionWrapper, tab: TabEntity) {
        viewModelScope.launch {
            session.url.combine(session.title) { u, t -> u to t }
                .distinctUntilChanged()
                .collect { (url, title) ->
                    if (url.isNotBlank()) {
                        tabRepo.updateTitleAndUrl(tab.id, title.ifBlank { url }, url)
                    }
                }
        }
    }
}

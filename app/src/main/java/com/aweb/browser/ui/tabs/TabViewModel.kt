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
) : ViewModel() {

    private val _uiState = MutableStateFlow(TabUiState())
    val uiState: StateFlow<TabUiState> = _uiState.asStateFlow()

    private val _activeWorkspace = MutableStateFlow<WorkspaceEntity?>(null)

    // Browser state exposed for BrowserScreen
    val url         get() = _uiState.value.activeSession?.url
    val title       get() = _uiState.value.activeSession?.title
    val progress    get() = _uiState.value.activeSession?.progress
    val loading     get() = _uiState.value.activeSession?.loading
    val canGoBack   get() = _uiState.value.activeSession?.canGoBack
    val canGoForward get() = _uiState.value.activeSession?.canGoForward

    init {
        viewModelScope.launch {
            _activeWorkspace
                .filterNotNull()
                .flatMapLatest { ws ->
                    tabRepo.observeTabsForWorkspace(ws.id).map { tabs -> ws to tabs }
                }
                .collect { (ws, rawTabs) ->

                    // Seed a homepage tab if workspace has no tabs yet
                    val tabs = if (rawTabs.isEmpty()) {
                        val newTab = tabRepo.createTab(workspaceId = ws.id)
                        tabRepo.setActiveTab(ws.id, newTab.id)
                        listOf(newTab.copy(isActive = true))
                    } else rawTabs

                    val prev   = _uiState.value.activeTab
                    val active = tabs.firstOrNull { it.isActive } ?: tabs.first()

                    // On first load for this workspace → restore from disk
                    val isWorkspaceSwitch = prev == null ||
                        prev.workspaceId != active.workspaceId

                    if (isWorkspaceSwitch) {
                        lifecycleManager.onAppRestore(tabs, ws)
                    }

                    val session = sessionManager.getOrCreate(active, ws)

                    // Persist url/title back to Room as user browses
                    wireSessionCallbacks(session, active)

                    // Keep AppState snapshot fresh for MemoryPressureReceiver
                    AppState.update(ws, tabs)

                    _uiState.update {
                        it.copy(tabs = tabs, activeTab = active, activeSession = session)
                    }
                }
        }
    }

    /** Called by MainActivity whenever the active workspace changes. */
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
        val ws   = _activeWorkspace.value ?: return
        val prev = _uiState.value.activeTab
        val all  = _uiState.value.tabs
        viewModelScope.launch {
            tabRepo.setActiveTab(ws.id, tab.id)
            // Let TabLifecycleManager handle session promotion/demotion/unloading
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
                if (next != null) {
                    tabRepo.setActiveTab(ws.id, next.id)
                } else {
                    tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
                }
            }
        }
    }

    fun closeAllTabs() {
        val ws   = _activeWorkspace.value ?: return
        val tabs = _uiState.value.tabs
        viewModelScope.launch {
            tabs.forEach { tab -> lifecycleManager.onTabClosed(tab, tabs, ws) }
            tabRepo.closeAllTabsInWorkspace(ws.id)
            tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
        }
    }

    fun setPinned(tab: TabEntity, pinned: Boolean) {
        viewModelScope.launch { tabRepo.setPinned(tab.id, pinned) }
    }

    fun setKeepAlive(tab: TabEntity, keepAlive: Boolean) {
        val ws  = _activeWorkspace.value ?: return
        val all = _uiState.value.tabs
        viewModelScope.launch {
            tabRepo.setKeepAlive(tab.id, keepAlive)
            // Rebalance immediately so the change is applied to live sessions
            if (!keepAlive) {
                // If un-marking keep alive, it may get unloaded by rebalance
                val updated = all.map { if (it.id == tab.id) it.copy(keepAlive = false) else it }
                lifecycleManager.onTabSelected(
                    newActiveTab      = all.first { it.isActive },
                    previousActiveTab = null,
                    allTabs           = updated,
                    workspace         = ws,
                )
            }
        }
    }

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

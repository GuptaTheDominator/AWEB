package com.aweb.browser.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.gecko.GeckoSessionWrapper
import com.aweb.browser.gecko.TabSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TabUiState(
    val tabs           : List<TabEntity>       = emptyList(),
    val activeTab      : TabEntity?            = null,
    val activeSession  : GeckoSessionWrapper?  = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TabViewModel @Inject constructor(
    private val tabRepo       : TabRepository,
    private val sessionManager: TabSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TabUiState())
    val uiState: StateFlow<TabUiState> = _uiState.asStateFlow()

    // The active workspace is set by the parent WorkspaceViewModel via [setWorkspace].
    private val _activeWorkspace = MutableStateFlow<WorkspaceEntity?>(null)

    // Browser state from the active tab's session
    val url        get() = _uiState.value.activeSession?.url
    val title      get() = _uiState.value.activeSession?.title
    val progress   get() = _uiState.value.activeSession?.progress
    val loading    get() = _uiState.value.activeSession?.loading
    val canGoBack  get() = _uiState.value.activeSession?.canGoBack
    val canGoForward get() = _uiState.value.activeSession?.canGoForward

    init {
        viewModelScope.launch {
            // React whenever the active workspace changes — reload tab list
            _activeWorkspace
                .filterNotNull()
                .flatMapLatest { ws ->
                    tabRepo.observeTabsForWorkspace(ws.id).map { tabs -> ws to tabs }
                }
                .collect { (ws, tabs) ->
                    // Seed a homepage tab if workspace has none yet
                    val tabList = if (tabs.isEmpty()) {
                        val newTab = tabRepo.createTab(workspaceId = ws.id)
                        tabRepo.setActiveTab(ws.id, newTab.id)
                        listOf(newTab.copy(isActive = true))
                    } else tabs

                    val active  = tabList.firstOrNull { it.isActive } ?: tabList.firstOrNull()
                    val session = active?.let { sessionManager.getOrCreate(it, ws) }

                    if (active != null) {
                        sessionManager.setActiveTab(active.id, tabList.map { it.id })
                    }

                    // Wire up title/url save-back from GeckoView → Room
                    session?.let { wireSessionCallbacks(it, active!!) }

                    _uiState.update {
                        it.copy(
                            tabs          = tabList,
                            activeTab     = active,
                            activeSession = session,
                        )
                    }
                }
        }
    }

    /** Called by WorkspaceViewModel whenever the user switches workspaces. */
    fun setWorkspace(workspace: WorkspaceEntity) {
        _activeWorkspace.value = workspace
    }

    // ── Tab actions ───────────────────────────────────────────────────────

    fun openNewTab(url: String = "https://duckduckgo.com") {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            val tab = tabRepo.createTab(workspaceId = ws.id, url = url)
            tabRepo.setActiveTab(ws.id, tab.id)
            // Session will be created by the collect block above reacting to Room change
        }
    }

    fun selectTab(tab: TabEntity) {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            tabRepo.setActiveTab(ws.id, tab.id)
        }
    }

    fun closeTab(tab: TabEntity) {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            val wasActive = tab.isActive
            sessionManager.unload(tab.id)
            tabRepo.closeTab(tab.id)

            // If we closed the active tab, switch to the first remaining tab
            if (wasActive) {
                val remaining = tabRepo.getTabsForWorkspace(ws.id)
                val next = remaining.firstOrNull()
                if (next != null) {
                    tabRepo.setActiveTab(ws.id, next.id)
                } else {
                    // All tabs closed — open a fresh homepage tab
                    tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
                }
            }
        }
    }

    fun closeAllTabs() {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            val tabs = tabRepo.getTabsForWorkspace(ws.id)
            sessionManager.closeAllForWorkspace(tabs.map { it.id })
            tabRepo.closeAllTabsInWorkspace(ws.id)
            tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
        }
    }

    fun setPinned(tab: TabEntity, pinned: Boolean) {
        viewModelScope.launch { tabRepo.setPinned(tab.id, pinned) }
    }

    fun setKeepAlive(tab: TabEntity, keepAlive: Boolean) {
        viewModelScope.launch { tabRepo.setKeepAlive(tab.id, keepAlive) }
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

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Subscribes to url/title changes from a GeckoSession and persists them
     * back to Room so tabs restore with correct title/url after app restart.
     */
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

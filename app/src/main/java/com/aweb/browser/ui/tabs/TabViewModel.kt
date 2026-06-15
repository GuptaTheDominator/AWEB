package com.aweb.browser.ui.tabs

import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "TabViewModel"

data class TabUiState(
    val tabs: List<TabEntity> = emptyList(),
    val activeTab: TabEntity? = null,
    val activeSession: GeckoSessionWrapper? = null,
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TabViewModel @Inject constructor(
    private val tabRepo: TabRepository,
    private val settingsRepo: com.aweb.browser.data.repository.SettingsRepository,
    private val sessionManager: TabSessionManager,
    private val lifecycleManager: TabLifecycleManager,
    private val keepAliveManager: KeepAliveManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TabUiState())
    val uiState: StateFlow<TabUiState> = _uiState.asStateFlow()
    val keepAliveEvent = keepAliveManager.event
    private val _activeWorkspace = MutableStateFlow<WorkspaceEntity?>(null)
    private var wireCallbacksJob: kotlinx.coroutines.Job? = null

    val url          get() = _uiState.value.activeSession?.url
    val title        get() = _uiState.value.activeSession?.title
    val progress     get() = _uiState.value.activeSession?.progress
    val loading      get() = _uiState.value.activeSession?.loading
    val canGoBack    get() = _uiState.value.activeSession?.canGoBack
    val canGoForward get() = _uiState.value.activeSession?.canGoForward
    val keepAliveCount get() = _uiState.value.tabs.count { it.keepAlive }
    val keepAliveCap   get() = lifecycleManager.policy.maxKeepAlive

    init {
        viewModelScope.launch {
            _activeWorkspace.filterNotNull()
                .flatMapLatest { ws ->
                    tabRepo.observeTabsForWorkspace(ws.id)
                        .distinctUntilChanged()
                        .catch { e -> Log.e(TAG, "tabObs: ${e.message}", e); emit(emptyList()) }
                        .map { tabs -> ws to tabs }
                }
                .catch { e ->
                    Log.e(TAG, "outerFlow: ${e.message}", e)
                    _uiState.update { it.copy(isError = true) }
                }
                .collect { (ws, rawTabs) -> safeCollect(ws, rawTabs) }
        }
    }

    private suspend fun safeCollect(ws: WorkspaceEntity, rawTabs: List<TabEntity>) {
        try {
            val tabs = if (rawTabs.isEmpty()) {
                try {
                    val t = tabRepo.createTab(workspaceId = ws.id)
                    tabRepo.setActiveTab(ws.id, t.id)
                    listOf(t.copy(isActive = true))
                } catch (e: Exception) { Log.e(TAG, "seedTab: ${e.message}", e); rawTabs }
            } else rawTabs

            val prev   = _uiState.value.activeTab
            val active = tabs.firstOrNull { it.isActive } ?: tabs.firstOrNull() ?: return

            if (prev == null || prev.workspaceId != active.workspaceId) {
                try { lifecycleManager.onAppRestore(tabs, ws) }
                catch (e: Exception) { Log.w(TAG, "onAppRestore: ${e.message}") }
            }

            val session = safeGetSession(active, ws)
            if (session != null) safeWireCallbacks(session, active)
            
            val engine = settingsRepo.defaultSearchEngine.first()
            AppState.update(ws, tabs, engine)
            
            _uiState.update { it.copy(tabs = tabs, activeTab = active, activeSession = session, isError = false) }
        } catch (e: Exception) {
            Log.e(TAG, "safeCollect: ${e.message}", e)
        }
    }

    private suspend fun safeGetSession(
        tab: TabEntity,
        workspace: WorkspaceEntity,
    ): GeckoSessionWrapper? {
        repeat(8) { attempt ->
            try {
                val wrapper = withContext(Dispatchers.Main) {
                    sessionManager.getOrCreate(tab, workspace)
                }
                // FIX (Bug 1): Wire the new-tab callback so target=_blank links open a real tab
                withContext(Dispatchers.Main) {
                    wrapper.onNewTabRequested = { uri ->
                        openNewTab(uri)
                    }
                }
                return wrapper
            } catch (e: Exception) {
                Log.w(TAG, "getOrCreate attempt ${attempt + 1}: ${e.message}")
                delay(300L * (attempt + 1))
            }
        }
        Log.e(TAG, "All session attempts failed for tab=${tab.id}")
        return null
    }

    fun setWorkspace(workspace: WorkspaceEntity) { _activeWorkspace.value = workspace }

    // FIX (Bug 7 / Bug 12): openNewTab with URL=about:newtab so getOrCreate
    // opens an empty session instead of navigating to duckduckgo immediately.
    // The tab entity stores the *intended* url = "" and gets opened as blank.
    fun openNewTab(url: String = "") {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            try {
                val finalUrl = url.ifBlank { "about:newtab" }
                val t = tabRepo.createTab(workspaceId = ws.id, url = finalUrl)
                tabRepo.setActiveTab(ws.id, t.id)
                // If a real URL was provided (e.g. from target=_blank), navigate to it
                if (url.isNotBlank() && url != "about:newtab" && url != "about:blank") {
                    // session will be created by safeCollect → safeGetSession → getOrCreate
                    // getOrCreate already calls loadUrl(url) for non-blank urls
                }
            } catch (e: Exception) {
                Log.e(TAG, "openNewTab: ${e.message}")
            }
        }
    }

    fun selectTab(tab: TabEntity) {
        val ws  = _activeWorkspace.value ?: return
        val prev = _uiState.value.activeTab
        val all  = _uiState.value.tabs
        viewModelScope.launch {
            try {
                tabRepo.setActiveTab(ws.id, tab.id)
                try { lifecycleManager.onTabSelected(tab, prev, all, ws) }
                catch (e: Exception) { Log.w(TAG, "lcSel: ${e.message}") }
            } catch (e: Exception) {
                Log.e(TAG, "selectTab: ${e.message}")
            }
        }
    }

    fun closeTab(tab: TabEntity) {
        val ws  = _activeWorkspace.value ?: return
        val all = _uiState.value.tabs
        viewModelScope.launch {
            try {
                val wasActive = tab.id == _uiState.value.activeTab?.id
                tabRepo.closeTab(tab.id)
                try { lifecycleManager.onTabClosed(tab, all, ws) }
                catch (e: Exception) { Log.w(TAG, "lcClose: ${e.message}") }
                if (wasActive) {
                    val next = tabRepo.getTabsForWorkspace(ws.id).firstOrNull()
                    if (next != null) tabRepo.setActiveTab(ws.id, next.id)
                    else tabRepo.ensureAtLeastOneTab(ws.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "closeTab: ${e.message}")
            }
        }
    }

    fun closeAllTabs() {
        val ws   = _activeWorkspace.value ?: return
        val tabs = _uiState.value.tabs
        viewModelScope.launch {
            try {
                tabs.forEach { try { lifecycleManager.onTabClosed(it, tabs, ws) } catch (_: Exception) {} }
                tabRepo.closeAllTabsInWorkspace(ws.id)
                tabRepo.ensureAtLeastOneTab(ws.id)
            } catch (e: Exception) {
                Log.e(TAG, "closeAll: ${e.message}")
            }
        }
    }

    fun setPinned(tab: TabEntity, pinned: Boolean) {
        viewModelScope.launch {
            try { tabRepo.setPinned(tab.id, pinned) }
            catch (e: Exception) { Log.e(TAG, "pin: ${e.message}") }
        }
    }

    fun toggleKeepAlive(tab: TabEntity) {
        val ws = _activeWorkspace.value ?: return
        try { keepAliveManager.toggle(tab, _uiState.value.tabs, ws) }
        catch (e: Exception) { Log.e(TAG, "ka: ${e.message}") }
    }

    fun disableKeepAlive(tab: TabEntity) { if (tab.keepAlive) toggleKeepAlive(tab) }
    fun clearKeepAliveEvent() = keepAliveManager.clearEvent()
    fun getKeepAliveTabs(): List<TabEntity> =
        try { keepAliveManager.getKeepAliveTabs(_uiState.value.tabs) } catch (_: Exception) { emptyList() }

    fun reorderTabs(orderedIds: List<String>) {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            try { tabRepo.reorder(ws.id, orderedIds) }
            catch (e: Exception) { Log.e(TAG, "reorder: ${e.message}") }
        }
    }

    /**
     * FIX (Bug 11): Improved URL/query detection.
     *  - Bare words with dots but no TLD pattern → search (e.g. "e.g." "file.txt")
     *  - Looks for at least one valid TLD-like segment (2+ alpha chars after the last dot)
     *  - Excludes inputs with spaces anywhere in what looks like a domain
     */
    fun loadUrl(input: String) {
        val session = _uiState.value.activeSession
            ?: _activeWorkspace.value?.let { ws ->
                _uiState.value.activeTab?.let { tab ->
                    sessionManager.get(tab.id)
                }
            }
            ?: return

        val trimmed = input.trim()
        val url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("about:") || trimmed.startsWith("file://") -> trimmed
            looksLikeDomain(trimmed) -> "https://$trimmed"
            else -> {
                // Use user-selected search engine
                val engine = com.aweb.browser.AppState.currentSearchEngine ?: com.aweb.browser.data.repository.SearchEngine.DUCKDUCKGO
                engine.buildSearchUrl(trimmed)
            }
        }
        try { session.loadUrl(url) } catch (e: Exception) { Log.e(TAG, "loadUrl: ${e.message}") }
    }

    fun goBack()    { try { _uiState.value.activeSession?.goBack() }      catch (_: Exception) {} }
    fun goForward() { try { _uiState.value.activeSession?.goForward() }   catch (_: Exception) {} }
    fun reload()    { try { _uiState.value.activeSession?.reload() }      catch (_: Exception) {} }
    fun stop()      { try { _uiState.value.activeSession?.stopLoading() } catch (_: Exception) {} }

    fun toggleDesktopMode() {
        val tab = _uiState.value.activeTab ?: return
        val current = tab.userAgentMode
        val next = if (current == "desktop") "mobile" else "desktop"
        viewModelScope.launch {
            tabRepo.updateUserAgentMode(tab.id, next)
            // Reload the session with new settings
            _uiState.value.activeSession?.let { wrapper ->
                wrapper.session?.let { sess ->
                    sess.settings.userAgentMode = if (next == "desktop") 
                        org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP 
                        else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                    sess.reload()
                }
            }
        }
    }

    /**
     * FIX (Bug 2): Only save to DB after a genuine page-load completion.
     * Previously the flow fired immediately with isLoading=false on first subscription,
     * causing blank URLs to overwrite the tab's real URL in Room.
     *
     * Fix: use distinctUntilChanged + only save when transitioning false AFTER
     * at least one true (i.e. a real page load happened).
     */
    private fun safeWireCallbacks(session: GeckoSessionWrapper, tab: TabEntity) {
        wireCallbacksJob?.cancel()
        wireCallbacksJob = viewModelScope.launch {
            try {
                var hasSeenLoading = false
                // Note: distinctUntilChanged on StateFlow is a no-op (StateFlow already
                // deduplicates); we track state manually with hasSeenLoading instead.
                session.loading
                    .catch { e -> Log.w(TAG, "loading flow: ${e.message}") }
                    .collect { isLoading ->
                        if (isLoading) {
                            hasSeenLoading = true
                        } else if (hasSeenLoading) {
                            // Page genuinely finished loading — save title + url
                            val url   = session.url.value
                            val title = session.title.value
                            if (url.isNotBlank() && !url.startsWith("about:")) {
                                try {
                                    tabRepo.updateTitleAndUrl(tab.id, title.ifBlank { url }, url)
                                } catch (e: Exception) {
                                    Log.w(TAG, "updateTitle: ${e.message}")
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "wireCallbacks: ${e.message}")
            }
        }
    }
}

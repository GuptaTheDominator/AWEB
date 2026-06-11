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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TabViewModel"

data class TabUiState(
    val tabs          : List<TabEntity>      = emptyList(),
    val activeTab     : TabEntity?           = null,
    val activeSession : GeckoSessionWrapper? = null,
    val isError       : Boolean              = false,
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

    val keepAliveEvent = keepAliveManager.event

    private val _activeWorkspace = MutableStateFlow<WorkspaceEntity?>(null)

    val url          get() = _uiState.value.activeSession?.url
    val title        get() = _uiState.value.activeSession?.title
    val progress     get() = _uiState.value.activeSession?.progress
    val loading      get() = _uiState.value.activeSession?.loading
    val canGoBack    get() = _uiState.value.activeSession?.canGoBack
    val canGoForward get() = _uiState.value.activeSession?.canGoForward
    val keepAliveCount  get() = _uiState.value.tabs.count { it.keepAlive }
    val keepAliveCap    get() = lifecycleManager.policy.maxKeepAlive

    init {
        viewModelScope.launch {
            _activeWorkspace
                .filterNotNull()
                .flatMapLatest { ws ->
                    tabRepo.observeTabsForWorkspace(ws.id)
                        .catch { e ->
                            Log.e(TAG, "Tab observation error: ${e.message}", e)
                            emit(emptyList())
                        }
                        .map { tabs -> ws to tabs }
                }
                .catch { e ->
                    Log.e(TAG, "Outer flow error: ${e.message}", e)
                    _uiState.update { it.copy(isError = true) }
                }
                .collect { (ws, rawTabs) ->
                    safeCollect(ws, rawTabs)
                }
        }
    }

    private suspend fun safeCollect(ws: WorkspaceEntity, rawTabs: List<TabEntity>) {
        try {
            // Seed a homepage tab if workspace has none
            val tabs = if (rawTabs.isEmpty()) {
                try {
                    val t = tabRepo.createTab(workspaceId = ws.id)
                    tabRepo.setActiveTab(ws.id, t.id)
                    listOf(t.copy(isActive = true))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to seed tab: ${e.message}", e)
                    rawTabs
                }
            } else rawTabs

            val prev   = _uiState.value.activeTab
            val active = tabs.firstOrNull { it.isActive } ?: tabs.firstOrNull()
                ?: return  // no tabs at all, nothing to show yet

            val isWorkspaceSwitch = prev == null || prev.workspaceId != active.workspaceId

            if (isWorkspaceSwitch) {
                try { lifecycleManager.onAppRestore(tabs, ws) }
                catch (e: Exception) { Log.w(TAG, "onAppRestore failed: ${e.message}") }
            }

            // Create Gecko session — wrap because GeckoRuntime may still be starting
            val session = safeGetSession(active, ws)

            if (session != null) {
                safeWireCallbacks(session, active)
            }

            AppState.update(ws, tabs)

            _uiState.update {
                it.copy(
                    tabs          = tabs,
                    activeTab     = active,
                    activeSession = session,
                    isError       = false,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "safeCollect error: ${e.message}", e)
        }
    }

    /**
     * Creates the GeckoSession on the Main thread with retries.
     * GeckoRuntime and GeckoSession.open() must run on Main — this is enforced
     * inside GeckoSessionWrapper.open() via Handler, but we also guard here.
     */
    private suspend fun safeGetSession(
        tab       : TabEntity,
        workspace : WorkspaceEntity,
    ): GeckoSessionWrapper? {
        repeat(5) { attempt ->
            try {
                // Switch to Main thread for GeckoView operations
                return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sessionManager.getOrCreate(tab, workspace)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Session create attempt ${attempt + 1} failed: ${e.message}")
                delay(500L * (attempt + 1))
            }
        }
        Log.e(TAG, "All session create attempts failed for tab '${tab.title}'")
        return null
    }

    fun setWorkspace(workspace: WorkspaceEntity) {
        _activeWorkspace.value = workspace
    }

    // ── Tab actions ───────────────────────────────────────────────────────

    fun openNewTab(url: String = "https://duckduckgo.com") {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            try {
                val tab = tabRepo.createTab(workspaceId = ws.id, url = url)
                tabRepo.setActiveTab(ws.id, tab.id)
            } catch (e: Exception) { Log.e(TAG, "openNewTab: ${e.message}") }
        }
    }

    fun selectTab(tab: TabEntity) {
        val ws   = _activeWorkspace.value ?: return
        val prev = _uiState.value.activeTab
        val all  = _uiState.value.tabs
        viewModelScope.launch {
            try {
                tabRepo.setActiveTab(ws.id, tab.id)
                try {
                    lifecycleManager.onTabSelected(
                        newActiveTab      = tab,
                        previousActiveTab = prev,
                        allTabs           = all,
                        workspace         = ws,
                    )
                } catch (e: Exception) { Log.w(TAG, "lifecycle onTabSelected: ${e.message}") }
            } catch (e: Exception) { Log.e(TAG, "selectTab: ${e.message}") }
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
                catch (e: Exception) { Log.w(TAG, "lifecycle onTabClosed: ${e.message}") }
                if (wasActive) {
                    val remaining = tabRepo.getTabsForWorkspace(ws.id)
                    val next = remaining.firstOrNull()
                    if (next != null) tabRepo.setActiveTab(ws.id, next.id)
                    else tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
                }
            } catch (e: Exception) { Log.e(TAG, "closeTab: ${e.message}") }
        }
    }

    fun closeAllTabs() {
        val ws   = _activeWorkspace.value ?: return
        val tabs = _uiState.value.tabs
        viewModelScope.launch {
            try {
                tabs.forEach {
                    try { lifecycleManager.onTabClosed(it, tabs, ws) }
                    catch (e: Exception) { Log.w(TAG, "lifecycle close: ${e.message}") }
                }
                tabRepo.closeAllTabsInWorkspace(ws.id)
                tabRepo.ensureAtLeastOneTab(ws.id, "https://duckduckgo.com")
            } catch (e: Exception) { Log.e(TAG, "closeAllTabs: ${e.message}") }
        }
    }

    fun setPinned(tab: TabEntity, pinned: Boolean) {
        viewModelScope.launch {
            try { tabRepo.setPinned(tab.id, pinned) }
            catch (e: Exception) { Log.e(TAG, "setPinned: ${e.message}") }
        }
    }

    fun toggleKeepAlive(tab: TabEntity) {
        val ws  = _activeWorkspace.value ?: return
        val all = _uiState.value.tabs
        try { keepAliveManager.toggle(tab, all, ws) }
        catch (e: Exception) { Log.e(TAG, "toggleKeepAlive: ${e.message}") }
    }

    fun disableKeepAlive(tab: TabEntity) {
        if (!tab.keepAlive) return
        toggleKeepAlive(tab)
    }

    fun clearKeepAliveEvent() = keepAliveManager.clearEvent()

    fun getKeepAliveTabs(): List<TabEntity> =
        try { keepAliveManager.getKeepAliveTabs(_uiState.value.tabs) }
        catch (e: Exception) { emptyList() }

    fun reorderTabs(orderedIds: List<String>) {
        val ws = _activeWorkspace.value ?: return
        viewModelScope.launch {
            try { tabRepo.reorder(ws.id, orderedIds) }
            catch (e: Exception) { Log.e(TAG, "reorderTabs: ${e.message}") }
        }
    }

    // ── Browser passthrough ───────────────────────────────────────────────

    fun loadUrl(input: String) {
        val session = _uiState.value.activeSession ?: return
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ")                  -> "https://$input"
            else -> "https://duckduckgo.com/?q=${input.trim().replace(" ", "+")}"
        }
        try { session.loadUrl(url) }
        catch (e: Exception) { Log.e(TAG, "loadUrl: ${e.message}") }
    }

    fun goBack()    { try { _uiState.value.activeSession?.goBack() }    catch (e: Exception) { Log.w(TAG, "goBack: ${e.message}") } }
    fun goForward() { try { _uiState.value.activeSession?.goForward() } catch (e: Exception) { Log.w(TAG, "goForward: ${e.message}") } }
    fun reload()    { try { _uiState.value.activeSession?.reload() }    catch (e: Exception) { Log.w(TAG, "reload: ${e.message}") } }
    fun stop()      { try { _uiState.value.activeSession?.stopLoading() } catch (e: Exception) { Log.w(TAG, "stop: ${e.message}") } }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun safeWireCallbacks(session: GeckoSessionWrapper, tab: TabEntity) {
        viewModelScope.launch {
            try {
                session.url.combine(session.title) { u, t -> u to t }
                    .distinctUntilChanged()
                    .catch { e -> Log.w(TAG, "url/title flow error: ${e.message}") }
                    .collect { (url, title) ->
                        if (url.isNotBlank()) {
                            try { tabRepo.updateTitleAndUrl(tab.id, title.ifBlank { url }, url) }
                            catch (e: Exception) { Log.w(TAG, "updateTitleAndUrl: ${e.message}") }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "wireCallbacks outer: ${e.message}")
            }
        }
    }
}

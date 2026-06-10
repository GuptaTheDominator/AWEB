package com.aweb.browser.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.gecko.GeckoSessionWrapper
import com.aweb.browser.gecko.WorkspaceSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkspaceUiState(
    val workspaces      : List<WorkspaceEntity> = emptyList(),
    val activeWorkspace : WorkspaceEntity?      = null,
    val activeSession   : GeckoSessionWrapper?  = null,
    val isLoading       : Boolean               = true,
    val showCreateDialog: Boolean               = false,
    val showDeleteDialog: WorkspaceEntity?      = null,  // non-null = show confirm for this ws
    val errorMessage    : String?               = null,
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val repo          : WorkspaceRepository,
    private val sessionManager: WorkspaceSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    // Expose active session's browser state for BrowserScreen consumption
    val url        get() = _uiState.value.activeSession?.url
    val title      get() = _uiState.value.activeSession?.title
    val progress   get() = _uiState.value.activeSession?.progress
    val loading    get() = _uiState.value.activeSession?.loading
    val canGoBack  get() = _uiState.value.activeSession?.canGoBack
    val canGoForward get() = _uiState.value.activeSession?.canGoForward

    init {
        viewModelScope.launch {
            // Ensure at least one workspace exists on first ever launch
            repo.ensureDefaultWorkspace()

            // Observe workspace list and keep UI state in sync
            repo.workspaces.collect { list ->
                val active  = list.firstOrNull { it.isActive } ?: list.firstOrNull()
                val session = active?.let { sessionManager.getOrCreate(it) }

                _uiState.update {
                    it.copy(
                        workspaces      = list,
                        activeWorkspace = active,
                        activeSession   = session,
                        isLoading       = false,
                    )
                }
            }
        }
    }

    // ── Workspace actions ─────────────────────────────────────────────────

    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            // Pause old sessions
            sessionManager.pauseAllExcept(workspace.id)

            // Activate in DB
            repo.switchActive(workspace.id)

            // Get or create session (contextId already stored in workspace)
            val session = sessionManager.getOrCreate(workspace)
            sessionManager.resume(workspace.id)

            _uiState.update { it.copy(activeWorkspace = workspace, activeSession = session) }
        }
    }

    fun showCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }

    fun dismissCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }

    fun createWorkspace(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val ws = repo.createWorkspace(name = name.trim(), colorHex = colorHex)
            _uiState.update { it.copy(showCreateDialog = false) }
            switchWorkspace(ws)
        }
    }

    fun renameWorkspace(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repo.renameWorkspace(id, newName.trim()) }
    }

    fun confirmDeleteWorkspace(workspace: WorkspaceEntity) {
        _uiState.update { it.copy(showDeleteDialog = workspace) }
    }

    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = null) }

    fun deleteWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            sessionManager.closeAndRemove(workspace.id)
            repo.deleteWorkspace(workspace.id)
            _uiState.update { it.copy(showDeleteDialog = null) }

            // If we deleted the active workspace, switch to first remaining one
            val remaining = repo.getAll()
            if (remaining.isNotEmpty()) {
                switchWorkspace(remaining.first())
            }
        }
    }

    fun clearWorkspaceData(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            sessionManager.clearWorkspaceData(workspace)
            // Reload homepage in fresh session
            sessionManager.get(workspace.id)?.loadUrl("https://duckduckgo.com")
        }
    }

    fun reorderWorkspaces(orderedIds: List<String>) {
        viewModelScope.launch { repo.reorder(orderedIds) }
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
}

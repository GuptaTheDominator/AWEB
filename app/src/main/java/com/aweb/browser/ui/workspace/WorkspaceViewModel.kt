package com.aweb.browser.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.AppState
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.SettingsRepository
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.ui.keepalive.KeepAliveManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkspaceUiState(
    val workspaces      : List<WorkspaceEntity> = emptyList(),
    val activeWorkspace : WorkspaceEntity?      = null,
    val isLoading       : Boolean               = true,
    val showCreateDialog: Boolean               = false,
    val showDeleteDialog: WorkspaceEntity?      = null,
    val errorMessage    : String?               = null,
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val repo            : WorkspaceRepository,
    private val settingsRepo    : SettingsRepository,
    private val lifecycleManager: TabLifecycleManager,
    private val keepAliveManager: KeepAliveManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureDefaultWorkspace()
            repo.workspaces.collect { list ->
                val active = list.firstOrNull { it.isActive } ?: list.firstOrNull()
                _uiState.update {
                    it.copy(workspaces = list, activeWorkspace = active, isLoading = false)
                }
            }
        }

        // Propagate memory mode + keep-alive cap changes to lifecycle manager
        viewModelScope.launch {
            combine(
                settingsRepo.memoryMode,
                settingsRepo.maxKeepAliveTabs,
            ) { mode, maxKa -> mode to maxKa }
                .distinctUntilChanged()
                .collect { (mode, maxKa) ->
                    lifecycleManager.applyMemoryMode(mode.key, maxKa)
                    // Enforce new cap on existing KA tabs immediately
                    keepAliveManager.enforceCap(AppState.currentTabs, maxKa)
                }
        }
    }

    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch { repo.switchActive(workspace.id) }
    }

    fun showCreateDialog()    = _uiState.update { it.copy(showCreateDialog = true) }
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

    fun confirmDeleteWorkspace(ws: WorkspaceEntity) =
        _uiState.update { it.copy(showDeleteDialog = ws) }

    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = null) }

    fun deleteWorkspace(ws: WorkspaceEntity) {
        viewModelScope.launch {
            repo.deleteWorkspace(ws.id)
            _uiState.update { it.copy(showDeleteDialog = null) }
            val remaining = repo.getAll()
            if (remaining.isNotEmpty()) switchWorkspace(remaining.first())
        }
    }

    fun clearWorkspaceData(ws: WorkspaceEntity) {
        // Gecko storage clear handled in TabSessionManager on next workspace restore
    }

    fun reorderWorkspaces(orderedIds: List<String>) {
        viewModelScope.launch { repo.reorder(orderedIds) }
    }
}

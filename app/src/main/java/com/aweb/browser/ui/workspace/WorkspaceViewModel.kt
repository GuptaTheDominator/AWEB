package com.aweb.browser.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.data.repository.SettingsRepository
import com.aweb.browser.lifecycle.TabLifecycleManager
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

        // Keep TabLifecycleManager in sync with memory mode setting changes
        viewModelScope.launch {
            combine(
                settingsRepo.memoryMode,
                settingsRepo.maxKeepAliveTabs,
            ) { mode, maxKa -> mode to maxKa }
                .distinctUntilChanged()
                .collect { (mode, maxKa) ->
                    lifecycleManager.applyMemoryMode(mode.key, maxKa)
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

    fun confirmDeleteWorkspace(workspace: WorkspaceEntity) =
        _uiState.update { it.copy(showDeleteDialog = workspace) }

    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = null) }

    fun deleteWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            repo.deleteWorkspace(workspace.id)
            _uiState.update { it.copy(showDeleteDialog = null) }
            val remaining = repo.getAll()
            if (remaining.isNotEmpty()) switchWorkspace(remaining.first())
        }
    }

    fun clearWorkspaceData(workspace: WorkspaceEntity) {
        // Gecko context storage clear — handled in TabViewModel/TabSessionManager
    }

    fun reorderWorkspaces(orderedIds: List<String>) {
        viewModelScope.launch { repo.reorder(orderedIds) }
    }
}

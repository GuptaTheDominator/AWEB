package com.aweb.browser.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.WorkspaceRepository
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

/**
 * Drives workspace list and active workspace.
 * Browser passthrough moved to [TabViewModel] in Phase 3.
 * Session management is now handled per-tab by [TabSessionManager].
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val repo: WorkspaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureDefaultWorkspace()
            repo.workspaces.collect { list ->
                val active = list.firstOrNull { it.isActive } ?: list.firstOrNull()
                _uiState.update {
                    it.copy(
                        workspaces      = list,
                        activeWorkspace = active,
                        isLoading       = false,
                    )
                }
            }
        }
    }

    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            repo.switchActive(workspace.id)
            // TabViewModel observes activeWorkspace from MainActivity and reacts
        }
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
        // Actual Gecko storage clearing handled in TabViewModel / TabSessionManager
        // Here we just dismiss any open dialog state if needed
    }

    fun reorderWorkspaces(orderedIds: List<String>) {
        viewModelScope.launch { repo.reorder(orderedIds) }
    }
}

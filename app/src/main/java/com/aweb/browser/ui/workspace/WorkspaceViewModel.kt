package com.aweb.browser.ui.workspace

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.AppState
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.SettingsRepository
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.gecko.GeckoRuntimeManager
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.gecko.WorkspaceSessionManager
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.ui.keepalive.KeepAliveManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WorkspaceViewModel"

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
    @ApplicationContext private val context: Context,
    private val repo                  : WorkspaceRepository,
    private val tabRepo               : TabRepository,
    private val settingsRepo          : SettingsRepository,
    private val lifecycleManager      : TabLifecycleManager,
    private val keepAliveManager      : KeepAliveManager,
    private val tabSessionManager     : TabSessionManager,
    private val workspaceSessionManager: WorkspaceSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        // Load workspaces
        viewModelScope.launch {
            try {
                repo.ensureDefaultWorkspace()
            } catch (e: Exception) {
                Log.e(TAG, "ensureDefaultWorkspace failed: ${e.message}", e)
            }

            try {
                repo.workspaces
                    .catch { e ->
                        Log.e(TAG, "Workspace flow error: ${e.message}", e)
                        emit(emptyList())
                    }
                    .collect { list ->
                        val active = list.firstOrNull { it.isActive } ?: list.firstOrNull()
                        _uiState.update {
                            it.copy(
                                workspaces      = list,
                                activeWorkspace = active,
                                isLoading       = false,
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Workspace collect failed: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // Propagate settings to lifecycle manager
        viewModelScope.launch {
            try {
                combine(
                    settingsRepo.memoryMode,
                    settingsRepo.maxRecentLiveTabs,
                    settingsRepo.maxKeepAliveTabs,
                ) { mode, maxRecent, maxKa -> Triple(mode, maxRecent, maxKa) }
                    .distinctUntilChanged()
                    .catch { e -> Log.w(TAG, "Settings flow error: ${e.message}") }
                    .collect { (mode, maxRecent, maxKa) ->
                        try {
                            lifecycleManager.applyMemoryMode(mode.key, maxRecent, maxKa)
                            keepAliveManager.enforceCap(AppState.currentTabs, maxKa)
                        } catch (e: Exception) {
                            Log.w(TAG, "applyMemoryMode: ${e.message}")
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Settings combine failed: ${e.message}")
            }
        }
    }

    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            try { repo.switchActive(workspace.id) }
            catch (e: Exception) { Log.e(TAG, "switchWorkspace: ${e.message}") }
        }
    }

    fun showCreateDialog()    = _uiState.update { it.copy(showCreateDialog = true) }
    fun dismissCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }

    fun createWorkspace(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val ws = repo.createWorkspace(name = name.trim(), colorHex = colorHex)
                _uiState.update { it.copy(showCreateDialog = false) }
                switchWorkspace(ws)
            } catch (e: Exception) {
                Log.e(TAG, "createWorkspace: ${e.message}")
                _uiState.update { it.copy(showCreateDialog = false) }
            }
        }
    }

    fun renameWorkspace(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            try { repo.renameWorkspace(id, newName.trim()) }
            catch (e: Exception) { Log.e(TAG, "renameWorkspace: ${e.message}") }
        }
    }

    fun confirmDeleteWorkspace(ws: WorkspaceEntity) =
        _uiState.update { it.copy(showDeleteDialog = ws) }

    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = null) }

    fun deleteWorkspace(ws: WorkspaceEntity) {
        viewModelScope.launch {
            try {
                val tabIds = tabRepo.getTabsForWorkspace(ws.id).map { it.id }
                tabSessionManager.closeAllForWorkspace(tabIds)
                workspaceSessionManager.closeAndRemove(ws.id)
                GeckoRuntimeManager.clearSessionContextData(context, ws.contextId)

                repo.deleteWorkspace(ws.id)
                _uiState.update { it.copy(showDeleteDialog = null) }
                val remaining = repo.getAll()
                if (remaining.isNotEmpty()) switchWorkspace(remaining.first())
            } catch (e: Exception) {
                Log.e(TAG, "deleteWorkspace: ${e.message}")
                _uiState.update { it.copy(showDeleteDialog = null) }
            }
        }
    }

    fun clearWorkspaceData(ws: WorkspaceEntity) {
        viewModelScope.launch {
            try {
                val tabIds = tabRepo.getTabsForWorkspace(ws.id).map { it.id }
                tabSessionManager.closeAllForWorkspace(tabIds)
                workspaceSessionManager.closeAndRemove(ws.id)
                GeckoRuntimeManager.clearSessionContextData(context, ws.contextId)
                Log.i(TAG, "Cleared Gecko session data for workspace '${ws.name}'")
            } catch (e: Exception) {
                Log.e(TAG, "clearWorkspaceData: ${e.message}")
            }
        }
    }

    fun reorderWorkspaces(orderedIds: List<String>) {
        viewModelScope.launch {
            try { repo.reorder(orderedIds) }
            catch (e: Exception) { Log.e(TAG, "reorderWorkspaces: ${e.message}") }
        }
    }
}

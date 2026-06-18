package com.aweb.browser.ui.hardening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.crash.CrashRecoveryManager
import com.aweb.browser.data.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HardeningUiState(
    val crashInfo       : CrashRecoveryManager.CrashInfo? = null,
    val showCrashBanner : Boolean                         = false,
)

@HiltViewModel
class HardeningViewModel @Inject constructor(
    private val crashManager: CrashRecoveryManager,
    private val workspaceRepo: WorkspaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardeningUiState())
    val uiState: StateFlow<HardeningUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val info = crashManager.checkForCrash()
                if (info != null) {
                    _uiState.value = HardeningUiState(crashInfo = info, showCrashBanner = true)
                }
                crashManager.markSessionStarted()
            } catch (e: Exception) {
                // Never let crash detection itself crash the app
                android.util.Log.w("HardeningViewModel", "Init error: ${e.message}")
            }
        }
    }

    fun dismissCrashBanner() {
        crashManager.clearCrashInfo()
        _uiState.value = HardeningUiState()
    }

    fun runIsolationCheck(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val workspaces = workspaceRepo.getAll()
                val contextIds = workspaces.map { it.contextId }
                val ok = workspaces.isNotEmpty() &&
                    contextIds.none { it.isBlank() } &&
                    contextIds.size == contextIds.toSet().size
                onResult(
                    if (ok) "✓ ${workspaces.size} workspace contextId(s) are unique"
                    else "✗ Workspace contextId isolation problem detected"
                )
            } catch (e: Exception) {
                onResult("✗ Isolation check failed: ${e.message}")
            }
        }
    }

    fun markClean() {
        try { crashManager.markSessionClean() } catch (_: Exception) {}
    }
}

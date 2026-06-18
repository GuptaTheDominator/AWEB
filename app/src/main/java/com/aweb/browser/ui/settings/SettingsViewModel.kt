package com.aweb.browser.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.data.repository.MemoryMode
import com.aweb.browser.data.repository.SearchEngine
import com.aweb.browser.data.repository.SettingsRepository
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.service.ServiceManager
import com.aweb.browser.service.ServicePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val memoryMode        : MemoryMode   = MemoryMode.BALANCED,
    val maxRecentLiveTabs : Int          = 2,
    val maxKeepAliveTabs  : Int          = 3,
    val defaultHomepage   : String       = "https://duckduckgo.com",
    val defaultSearch     : SearchEngine = SearchEngine.DUCKDUCKGO,
    val keepScreenAwake   : Boolean      = false,
    val survivalServiceEnabled: Boolean  = true,
    val trackerShieldEnabled  : Boolean  = true,
    val compactTabletDensity  : Boolean  = false,
    val liveSessionCount  : Int          = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo    : SettingsRepository,
    private val sessionManager  : TabSessionManager,
    private val lifecycleManager: TabLifecycleManager,
    private val serviceManager  : ServiceManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    settingsRepo.memoryMode,
                    settingsRepo.maxRecentLiveTabs,
                    settingsRepo.maxKeepAliveTabs,
                    settingsRepo.defaultHomepage,
                    settingsRepo.defaultSearchEngine,
                ) { mode, recent, ka, home, search ->
                    SettingsUiState(
                        memoryMode        = mode,
                        maxRecentLiveTabs = recent,
                        maxKeepAliveTabs  = ka,
                        defaultHomepage   = home,
                        defaultSearch     = search,
                        liveSessionCount  = sessionManager.liveSessionCount,
                    )
                }.combine(settingsRepo.keepScreenAwake) { state, awake ->
                    state.copy(
                        keepScreenAwake = awake,
                        survivalServiceEnabled = ServicePreferences.isEnabled(context),
                        trackerShieldEnabled = _uiState.value.trackerShieldEnabled,
                        compactTabletDensity = _uiState.value.compactTabletDensity,
                    )
                }.catch { e ->
                    android.util.Log.w("SettingsViewModel", "Settings flow error: ${e.message}")
                }.collect { state ->
                    _uiState.value = state.copy(liveSessionCount = sessionManager.liveSessionCount)
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Init error: ${e.message}")
            }
        }
    }

    // ── Memory mode ───────────────────────────────────────────────────────

    fun setMemoryMode(mode: MemoryMode) {
        viewModelScope.launch {
            settingsRepo.setMemoryMode(mode)
            // Apply preset limits immediately if user picks a preset
            when (mode) {
                MemoryMode.CONSERVATIVE -> {
                    settingsRepo.setMaxRecentLiveTabs(0)
                    settingsRepo.setMaxKeepAliveTabs(2)
                }
                MemoryMode.BALANCED -> {
                    settingsRepo.setMaxRecentLiveTabs(2)
                    settingsRepo.setMaxKeepAliveTabs(3)
                }
                MemoryMode.PERFORMANCE -> {
                    settingsRepo.setMaxRecentLiveTabs(5)
                    settingsRepo.setMaxKeepAliveTabs(5)
                }
            }
        }
    }

    fun setMaxRecentLiveTabs(count: Int) {
        viewModelScope.launch { settingsRepo.setMaxRecentLiveTabs(count.coerceIn(0, 10)) }
    }

    fun setMaxKeepAliveTabs(count: Int) {
        viewModelScope.launch { settingsRepo.setMaxKeepAliveTabs(count.coerceIn(1, 10)) }
    }

    // ── Browser prefs ─────────────────────────────────────────────────────

    fun setDefaultHomepage(url: String) {
        val safe = if (url.isBlank()) "https://duckduckgo.com"
                   else if (!url.startsWith("http")) "https://$url"
                   else url
        viewModelScope.launch { settingsRepo.setDefaultHomepage(safe) }
    }

    fun setDefaultSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsRepo.setDefaultSearchEngine(engine) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setKeepScreenAwake(enabled) }
    }

    fun setSurvivalServiceEnabled(enabled: Boolean) {
        ServicePreferences.setEnabled(context, enabled)
        if (enabled) serviceManager.startService(context) else serviceManager.stopService(context)
        _uiState.update { it.copy(survivalServiceEnabled = enabled) }
    }

    fun setTrackerShieldEnabled(enabled: Boolean) {
        // GeckoRuntime currently enables anti-tracking by default. This state keeps
        // the new control-center UI responsive until a runtime setting is exposed.
        _uiState.update { it.copy(trackerShieldEnabled = enabled) }
    }

    fun setCompactTabletDensity(enabled: Boolean) {
        _uiState.update { it.copy(compactTabletDensity = enabled) }
    }

    fun resetUiPreferences() {
        _uiState.update { it.copy(trackerShieldEnabled = true, compactTabletDensity = false) }
    }

    // ── Live session count refresh ─────────────────────────────────────────
    // Called by SettingsScreen whenever it becomes visible

    fun refreshLiveSessionCount() {
        _uiState.update { it.copy(liveSessionCount = sessionManager.liveSessionCount) }
    }

    // ── Pressure simulation ────────────────────────────────────────────────
    // Triggers the same code path that Android calls on real memory pressure.
    // Useful for verifying policy behaviour without needing a low-memory device.

    fun simulatePressure(trimLevel: Int) {
        val ws   = com.aweb.browser.AppState.currentWorkspace ?: return
        val tabs = com.aweb.browser.AppState.currentTabs
        lifecycleManager.onMemoryPressure(trimLevel, tabs, ws)
        // Refresh the live count after unloading
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            refreshLiveSessionCount()
        }
    }
}

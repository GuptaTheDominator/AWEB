package com.aweb.browser.ui.hardening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.crash.CrashRecoveryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HardeningUiState(
    val crashInfo        : CrashRecoveryManager.CrashInfo? = null,
    val showCrashBanner  : Boolean                         = false,
)

@HiltViewModel
class HardeningViewModel @Inject constructor(
    private val crashManager: CrashRecoveryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardeningUiState())
    val uiState: StateFlow<HardeningUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val info = crashManager.checkForCrash()
            if (info != null) {
                _uiState.value = HardeningUiState(crashInfo = info, showCrashBanner = true)
            }
            crashManager.markSessionStarted()
        }
    }

    fun dismissCrashBanner() {
        crashManager.clearCrashInfo()
        _uiState.value = _uiState.value.copy(showCrashBanner = false, crashInfo = null)
    }

    fun markClean() = crashManager.markSessionClean()
}

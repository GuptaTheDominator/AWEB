package com.aweb.browser.ui.setup

import android.content.Context
import android.os.PowerManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.setupDataStore by preferencesDataStore("aweb_setup")
private val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _setupDone = MutableStateFlow(true)   // default true = don't show on first launch if DataStore fails
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    val batteryOptimizationIgnored: Boolean
        get() = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) { false }

    init {
        viewModelScope.launch {
            try {
                context.setupDataStore.data
                    .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                    .map { it[KEY_SETUP_DONE] ?: false }
                    .collect { _setupDone.value = it }
            } catch (e: Exception) {
                android.util.Log.w("SetupViewModel", "DataStore read failed: ${e.message}")
                _setupDone.value = true  // don't show setup if we can't read state
            }
        }
    }

    fun markSetupDone() {
        viewModelScope.launch {
            try {
                context.setupDataStore.edit { it[KEY_SETUP_DONE] = true }
            } catch (e: Exception) {
                android.util.Log.w("SetupViewModel", "markSetupDone failed: ${e.message}")
            }
        }
    }
}

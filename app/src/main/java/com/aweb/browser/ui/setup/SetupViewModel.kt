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

/**
 * Tracks whether the HyperOS setup guide has been completed.
 *
 * - [setupDone]: persisted in DataStore; once set to true the setup
 *   screen is not shown again on launch (user can still reach it via Settings).
 * - [batteryOptimizationIgnored]: live check via PowerManager.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    val batteryOptimizationIgnored: Boolean
        get() {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

    init {
        viewModelScope.launch {
            context.setupDataStore.data
                .map { it[KEY_SETUP_DONE] ?: false }
                .collect { _setupDone.value = it }
        }
    }

    fun markSetupDone() {
        viewModelScope.launch {
            context.setupDataStore.edit { it[KEY_SETUP_DONE] = true }
        }
    }
}

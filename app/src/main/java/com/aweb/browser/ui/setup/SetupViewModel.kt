package com.aweb.browser.ui.setup

import android.content.Context
import android.os.PowerManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.setupDataStore by preferencesDataStore("aweb_setup")
private val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")
private val KEY_COMPLETED_STEPS = stringSetPreferencesKey("completed_steps")

object HyperOsSetupStepIds {
    const val AUTOSTART = "autostart"
    const val BATTERY_OPTIMIZATION = "battery_optimization"
    const val HYPEROS_BATTERY_SAVER = "hyperos_battery_saver"
    const val LOCK_RECENTS = "lock_recents"
    const val NOTIFICATIONS = "notifications"
    const val KEEP_SCREEN_AWAKE = "keep_screen_awake"

    val REQUIRED = setOf(
        AUTOSTART,
        BATTERY_OPTIMIZATION,
        HYPEROS_BATTERY_SAVER,
        LOCK_RECENTS,
        NOTIFICATIONS,
    )
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _setupDone = MutableStateFlow(true)   // default true = don't show on first launch if DataStore fails
    val setupDone: StateFlow<Boolean> = _setupDone.asStateFlow()

    private val _completedSteps = MutableStateFlow<Set<String>>(emptySet())
    val completedSteps: StateFlow<Set<String>> = _completedSteps.asStateFlow()

    val batteryOptimizationIgnored: Boolean
        get() = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) { false }

    init {
        viewModelScope.launch {
            try {
                context.setupDataStore.data
                    .catch { emit(emptyPreferences()) }
                    .map { prefs ->
                        (prefs[KEY_SETUP_DONE] ?: false) to
                            (prefs[KEY_COMPLETED_STEPS] ?: emptySet())
                    }
                    .collect { (done, steps) ->
                        _setupDone.value = done
                        _completedSteps.value = steps
                    }
            } catch (e: Exception) {
                android.util.Log.w("SetupViewModel", "DataStore read failed: ${e.message}")
                _setupDone.value = true  // don't show setup if we can't read state
            }
        }
    }

    fun setStepDone(stepId: String, done: Boolean) {
        viewModelScope.launch {
            try {
                context.setupDataStore.edit { prefs ->
                    val current = prefs[KEY_COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
                    if (done) current.add(stepId) else current.remove(stepId)
                    prefs[KEY_COMPLETED_STEPS] = current
                    prefs[KEY_SETUP_DONE] = HyperOsSetupStepIds.REQUIRED.all { it in current }
                }
            } catch (e: Exception) {
                android.util.Log.w("SetupViewModel", "setStepDone failed: ${e.message}")
            }
        }
    }

    fun markSetupDone() {
        viewModelScope.launch {
            try {
                context.setupDataStore.edit { prefs ->
                    val current = prefs[KEY_COMPLETED_STEPS]?.toMutableSet() ?: mutableSetOf()
                    current.addAll(HyperOsSetupStepIds.REQUIRED)
                    prefs[KEY_COMPLETED_STEPS] = current
                    prefs[KEY_SETUP_DONE] = true
                }
            } catch (e: Exception) {
                android.util.Log.w("SetupViewModel", "markSetupDone failed: ${e.message}")
            }
        }
    }
}

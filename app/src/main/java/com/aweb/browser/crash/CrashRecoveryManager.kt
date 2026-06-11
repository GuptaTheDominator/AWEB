package com.aweb.browser.crash

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aweb.browser.AppState
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.lifecycle.TabLifecycleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.crashDataStore by preferencesDataStore("aweb_crash")

/**
 * Detects, records, and recovers from app crashes.
 *
 * Strategy:
 *  1. On every cold start, write a "session_started" flag to DataStore.
 *  2. On clean exit (onStop of MainActivity), write "session_clean".
 *  3. If on the next launch "session_started" is set but "session_clean" is NOT,
 *     we know the previous session ended abnormally (crash or process kill).
 *  4. On a detected crash: restore workspaces and tabs from Room as normal
 *     (they are always persisted), but show a [CrashRecoveryBanner] to inform
 *     the user and offer a "clear all sessions" escape hatch.
 *
 * Also installs a [Thread.UncaughtExceptionHandler] that writes a short
 * crash summary to DataStore before the process dies, so the next launch
 * can show the last crash message in the banner.
 */
@Singleton
class CrashRecoveryManager @Inject constructor(
    @ApplicationContext private val context : Context,
    private val workspaceRepo              : WorkspaceRepository,
    private val tabRepo                    : TabRepository,
    private val lifecycleManager           : TabLifecycleManager,
) {
    companion object {
        private const val TAG = "CrashRecoveryManager"

        private val KEY_SESSION_STARTED = booleanPreferencesKey("session_started")
        private val KEY_SESSION_CLEAN   = booleanPreferencesKey("session_clean")
        private val KEY_CRASH_MESSAGE   = stringPreferencesKey("last_crash_message")
        private val KEY_CRASH_TIME      = longPreferencesKey("last_crash_time")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Crash detection ───────────────────────────────────────────────────

    /** Call from Application.onCreate() — installs the uncaught exception hook. */
    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)

            // Write crash info synchronously before process dies
            val msg = "${throwable.javaClass.simpleName}: ${throwable.message}"
            scope.launch {
                context.crashDataStore.edit { prefs ->
                    prefs[KEY_SESSION_CLEAN]   = false
                    prefs[KEY_CRASH_MESSAGE]   = msg
                    prefs[KEY_CRASH_TIME]       = System.currentTimeMillis()
                }
            }
            // Let the default handler show the crash dialog
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Called on every cold start — marks the session as started (not yet clean). */
    fun markSessionStarted() {
        scope.launch {
            context.crashDataStore.edit { prefs ->
                prefs[KEY_SESSION_STARTED] = true
                prefs[KEY_SESSION_CLEAN]   = false
            }
            Log.d(TAG, "Session started")
        }
    }

    /** Called from MainActivity.onStop() — marks session as clean exit. */
    fun markSessionClean() {
        scope.launch {
            context.crashDataStore.edit { prefs ->
                prefs[KEY_SESSION_CLEAN] = true
            }
            Log.d(TAG, "Session marked clean")
        }
    }

    /**
     * Returns crash recovery info if the previous session was not clean.
     * Returns null if everything is fine.
     */
    suspend fun checkForCrash(): CrashInfo? {
        val prefs   = context.crashDataStore.data.first()
        val started = prefs[KEY_SESSION_STARTED] ?: false
        val clean   = prefs[KEY_SESSION_CLEAN]   ?: true
        val message = prefs[KEY_CRASH_MESSAGE]   ?: ""
        val time    = prefs[KEY_CRASH_TIME]       ?: 0L

        return if (started && !clean) {
            Log.w(TAG, "Previous session was not clean — crash detected")
            CrashInfo(lastCrashMessage = message, lastCrashTime = time)
        } else null
    }

    fun clearCrashInfo() {
        scope.launch {
            context.crashDataStore.edit { prefs ->
                prefs.remove(KEY_CRASH_MESSAGE)
                prefs.remove(KEY_CRASH_TIME)
            }
        }
    }

    // ── Session restoration ───────────────────────────────────────────────

    /**
     * Full session restore after crash or cold start.
     *
     * - Loads all workspaces from Room.
     * - For each workspace, loads its tabs.
     * - Calls [TabLifecycleManager.onAppRestore] so only the active tab
     *   and keep-alive tabs get live sessions — everything else stays unloaded.
     */
    suspend fun restoreSession() {
        Log.i(TAG, "Restoring session from Room")
        val workspaces = workspaceRepo.getAll()
        if (workspaces.isEmpty()) {
            Log.w(TAG, "No workspaces found — first launch or data cleared")
            return
        }

        val activeWs = workspaces.firstOrNull { it.isActive } ?: workspaces.first()
        val tabs     = tabRepo.getTabsForWorkspace(activeWs.id)

        if (tabs.isNotEmpty()) {
            lifecycleManager.onAppRestore(tabs, activeWs)
            AppState.update(activeWs, tabs)
            Log.i(TAG, "Restored ${tabs.size} tabs for workspace '${activeWs.name}'")
        }
    }

    data class CrashInfo(
        val lastCrashMessage: String,
        val lastCrashTime   : Long,
    )
}

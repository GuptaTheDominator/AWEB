package com.aweb.browser.crash

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aweb.browser.AppState
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.security.PrivacySanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.crashDataStore by preferencesDataStore("aweb_crash")

/**
 * Detects and records app crashes using a clean/unclean exit pattern.
 *
 * Strategy:
 *  - On start: session_started=true, session_clean=false
 *  - On clean exit (DisposableEffect.onDispose): session_clean=true
 *  - On crash: UncaughtExceptionHandler writes crash message, session_clean stays false
 *  - Next launch: session_started=true + session_clean=false → crash detected
 */
class CrashRecoveryManager(
    private val context       : Context,
    private val workspaceRepo : WorkspaceRepository,
    private val tabRepo       : TabRepository,
    private val lifecycleMgr  : TabLifecycleManager,
) {
    companion object {
        private const val TAG = "CrashRecoveryManager"
        private val KEY_STARTED = booleanPreferencesKey("session_started")
        private val KEY_CLEAN   = booleanPreferencesKey("session_clean")
        private val KEY_MSG     = stringPreferencesKey("last_crash_message")
        private val KEY_TIME    = longPreferencesKey("last_crash_time")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class CrashInfo(val lastCrashMessage: String, val lastCrashTime: Long)

    /** Register the uncaught exception handler. Call once from Application.onCreate(). */
    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on ${thread.name}: ${PrivacySanitizer.redact(throwable.message)}")
            try {
                // runBlocking is intentional here: the process is about to die
                // and we MUST write synchronously before the JVM exits.
                // This runs on the UncaughtExceptionHandler thread (not Main), so no deadlock.
                @Suppress("BlockingMethodInNonBlockingContext")
                runBlocking {
                    context.crashDataStore.edit { prefs ->
                        prefs[KEY_CLEAN] = false
                        prefs[KEY_MSG]   = PrivacySanitizer.redact("${throwable.javaClass.simpleName}: ${throwable.message}")
                        prefs[KEY_TIME]  = System.currentTimeMillis()
                    }
                }
            } catch (_: Exception) { /* can't crash the crash handler */ }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun markSessionStarted() {
        scope.launch {
            try {
                context.crashDataStore.edit { prefs ->
                    prefs[KEY_STARTED] = true
                    prefs[KEY_CLEAN]   = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "markSessionStarted failed: ${e.message}")
            }
        }
    }

    fun markSessionClean() {
        scope.launch {
            try {
                context.crashDataStore.edit { it[KEY_CLEAN] = true }
            } catch (e: Exception) {
                Log.w(TAG, "markSessionClean failed: ${e.message}")
            }
        }
    }

    suspend fun checkForCrash(): CrashInfo? {
        return try {
            val prefs   = context.crashDataStore.data.first()
            val started = prefs[KEY_STARTED] ?: false
            val clean   = prefs[KEY_CLEAN]   ?: true
            if (started && !clean) {
                CrashInfo(
                    lastCrashMessage = prefs[KEY_MSG]  ?: "",
                    lastCrashTime    = prefs[KEY_TIME] ?: 0L,
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "checkForCrash failed: ${e.message}")
            null
        }
    }

    fun clearCrashInfo() {
        scope.launch {
            try {
                context.crashDataStore.edit { prefs ->
                    prefs.remove(KEY_MSG)
                    prefs.remove(KEY_TIME)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun restoreSession() {
        Log.i(TAG, "Restoring session from Room")
        try {
            val workspaces = workspaceRepo.getAll()
            if (workspaces.isEmpty()) return
            val activeWs = workspaces.firstOrNull { it.isActive } ?: workspaces.first()
            val tabs     = tabRepo.getTabsForWorkspace(activeWs.id)
            if (tabs.isNotEmpty()) {
                lifecycleMgr.onAppRestore(tabs, activeWs)
                AppState.update(activeWs, tabs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreSession failed: ${e.message}")
        }
    }
}

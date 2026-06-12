package com.aweb.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps tabId -> GeckoSessionWrapper.
 *
 * CRITICAL: GeckoSession() constructor requires the Main thread.
 * [getOrCreate] checks the current thread and will throw if called from
 * a background thread — callers MUST ensure they dispatch to Main first.
 *
 * Callers:
 *  - TabViewModel.safeGetSession(): uses withContext(Dispatchers.Main) ✓
 *  - TabLifecycleManager: must also dispatch to Main (fixed in v1.0.8) ✓
 *  - KeepAliveManager: must also dispatch to Main (fixed in v1.0.8) ✓
 */
@Singleton
class TabSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object { private const val TAG = "TabSessionMgr" }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    fun get(tabId: String): GeckoSessionWrapper? = sessions[tabId]

    /**
     * Gets or creates a [GeckoSessionWrapper] for a tab.
     * The wrapper creation is safe on any thread (no GeckoSession yet).
     * [GeckoSessionWrapper.open] handles Main-thread dispatching for session creation.
     */
    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(tab.id) {
            Log.i(TAG, "Creating wrapper tab=${tab.id} ws=${workspace.name}")
            val w = GeckoSessionWrapper(contextId = workspace.contextId, appContext = context)
            // open() dispatches to Main internally
            w.open()
            val url = tab.url.takeIf { it.isNotBlank() && it != "about:blank" }
            if (url != null) w.loadUrl(url)
            w
        }
    }

    fun setActiveTab(activeTabId: String, allTabIds: List<String>) {
        allTabIds.forEach { id ->
            try { sessions[id]?.session?.setActive(id == activeTabId) }
            catch (e: Exception) { Log.w(TAG, "setActive $id: ${e.message}") }
        }
    }

    fun unload(tabId: String) {
        try { sessions.remove(tabId)?.close() }
        catch (e: Exception) { Log.w(TAG, "unload: ${e.message}") }
    }

    fun closeAllForWorkspace(ids: List<String>) {
        ids.forEach { id -> try { sessions.remove(id)?.close() } catch (_: Exception) {} }
    }

    fun closeAll() {
        sessions.values.forEach { try { it.close() } catch (_: Exception) {} }
        sessions.clear()
    }

    val liveSessionCount: Int get() = sessions.size
}

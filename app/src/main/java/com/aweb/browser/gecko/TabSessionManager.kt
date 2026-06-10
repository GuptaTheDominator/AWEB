package com.aweb.browser.gecko

import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the mapping of tab → [GeckoSessionWrapper].
 *
 * In Phase 2, one session was shared per workspace.
 * In Phase 3, each tab has its own [GeckoSessionWrapper] — but all tabs
 * within a workspace share the same [contextId] so they remain isolated
 * from other workspaces while sharing cookies/logins with each other.
 *
 * Memory strategy (Phase 4 will expand this):
 *  - Active tab: session open, setActive(true)
 *  - Other tabs: session may be open but setActive(false) to reduce resource use
 *  - Unloaded tabs: session closed entirely (loaded lazily on demand)
 *
 * Key: tabId (String)   Value: GeckoSessionWrapper
 */
@Singleton
class TabSessionManager @Inject constructor() {

    companion object {
        private const val TAG = "TabSessionManager"
    }

    // tabId -> wrapper
    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    /**
     * Returns the live [GeckoSessionWrapper] for a tab, or null if unloaded.
     */
    fun get(tabId: String): GeckoSessionWrapper? = sessions[tabId]

    /**
     * Returns the session for a tab, creating and opening one if it doesn't exist.
     * Uses the workspace's contextId so all tabs in the same workspace share cookies.
     */
    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(tab.id) {
            Log.i(TAG, "Creating session for tab '${tab.title}' in workspace '${workspace.name}'")
            GeckoSessionWrapper(contextId = workspace.contextId).also { wrapper ->
                wrapper.open()
                if (tab.url.isNotBlank()) wrapper.loadUrl(tab.url)
            }
        }
    }

    /**
     * Makes [activeTabId] the foreground session (Gecko prioritises it).
     * All other tabs in [workspaceId] are set inactive.
     */
    fun setActiveTab(activeTabId: String, allTabIds: List<String>) {
        allTabIds.forEach { id ->
            sessions[id]?.session?.setActive(id == activeTabId)
        }
        Log.d(TAG, "Active tab set to $activeTabId")
    }

    /**
     * Closes the GeckoSession for a tab to free memory.
     * The tab still exists in the DB — it will be recreated when selected.
     */
    fun unload(tabId: String) {
        sessions.remove(tabId)?.close()
        Log.d(TAG, "Unloaded tab session $tabId")
    }

    /**
     * Closes all sessions for a given workspace (e.g. workspace deleted).
     */
    fun closeAllForWorkspace(workspaceTabIds: List<String>) {
        workspaceTabIds.forEach { id ->
            sessions.remove(id)?.close()
        }
        Log.i(TAG, "Closed all sessions for ${workspaceTabIds.size} tabs")
    }

    /**
     * Closes all sessions for every tab. Call on app exit.
     */
    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        Log.i(TAG, "All tab sessions closed")
    }

    /** How many sessions are currently live (for memory debugging). */
    val liveSessionCount: Int get() = sessions.size
}

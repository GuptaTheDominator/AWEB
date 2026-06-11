package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the mapping of tab → [GeckoSessionWrapper].
 *
 * Each tab has its own GeckoSessionWrapper. All tabs in the same workspace
 * share the workspace's contextId for cookie/storage isolation.
 */
@Singleton
class TabSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "TabSessionManager"
    }

    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    fun get(tabId: String): GeckoSessionWrapper? = sessions[tabId]

    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(tab.id) {
            Log.i(TAG, "Creating session for tab '${tab.title}' ws='${workspace.name}'")
            GeckoSessionWrapper(
                contextId    = workspace.contextId,
                appContext   = context,
            ).also { wrapper ->
                wrapper.open()
                if (tab.url.isNotBlank() && tab.url != "about:blank") {
                    wrapper.loadUrl(tab.url)
                }
            }
        }
    }

    fun setActiveTab(activeTabId: String, allTabIds: List<String>) {
        allTabIds.forEach { id ->
            sessions[id]?.session?.setActive(id == activeTabId)
        }
        Log.d(TAG, "Active tab set: $activeTabId")
    }

    fun unload(tabId: String) {
        sessions.remove(tabId)?.close()
        Log.d(TAG, "Unloaded tab $tabId")
    }

    fun closeAllForWorkspace(workspaceTabIds: List<String>) {
        workspaceTabIds.forEach { id -> sessions.remove(id)?.close() }
        Log.i(TAG, "Closed ${workspaceTabIds.size} sessions for workspace")
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        Log.i(TAG, "All sessions closed")
    }

    val liveSessionCount: Int get() = sessions.size
}

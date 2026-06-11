package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "TabSessionManager"
    }

    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    fun get(tabId: String): GeckoSessionWrapper? = sessions[tabId]

    /**
     * Gets or creates a GeckoSessionWrapper for a tab.
     * Throws if GeckoRuntime fails — caller (TabViewModel.safeGetSession) handles retries.
     */
    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(tab.id) {
            Log.i(TAG, "Creating session tab='${tab.title}' ws='${workspace.name}'")
            val wrapper = GeckoSessionWrapper(
                contextId  = workspace.contextId,
                appContext = context,
            )
            wrapper.open()   // may throw if GeckoRuntime not ready — let caller retry
            val safeUrl = tab.url.takeIf { it.isNotBlank() && it != "about:blank" }
            if (safeUrl != null) {
                try { wrapper.loadUrl(safeUrl) }
                catch (e: Exception) { Log.w(TAG, "Initial loadUrl failed: ${e.message}") }
            }
            wrapper
        }
    }

    fun setActiveTab(activeTabId: String, allTabIds: List<String>) {
        allTabIds.forEach { id ->
            try { sessions[id]?.session?.setActive(id == activeTabId) }
            catch (e: Exception) { Log.w(TAG, "setActive($id): ${e.message}") }
        }
    }

    fun unload(tabId: String) {
        try { sessions.remove(tabId)?.close() }
        catch (e: Exception) { Log.w(TAG, "unload($tabId): ${e.message}") }
        Log.d(TAG, "Unloaded tab $tabId")
    }

    fun closeAllForWorkspace(workspaceTabIds: List<String>) {
        workspaceTabIds.forEach { id ->
            try { sessions.remove(id)?.close() }
            catch (e: Exception) { Log.w(TAG, "closeForWs($id): ${e.message}") }
        }
    }

    fun closeAll() {
        sessions.values.forEach {
            try { it.close() } catch (e: Exception) { Log.w(TAG, "closeAll: ${e.message}") }
        }
        sessions.clear()
    }

    val liveSessionCount: Int get() = sessions.size
}

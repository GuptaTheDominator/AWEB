package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps tabId -> [GeckoSessionWrapper].
 *
 * Thread safety: [GeckoSessionWrapper] constructor is safe on any thread.
 * All GeckoSession operations (open, loadUri) are dispatched to Main
 * internally by [GeckoSessionWrapper].
 */
@Singleton
class TabSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object { private const val TAG = "TabSessionMgr" }

    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    fun get(tabId: String): GeckoSessionWrapper? = sessions[tabId]

    /**
     * Gets or creates a [GeckoSessionWrapper] for a tab.
     * Safe to call from any thread — all GeckoView operations are
     * dispatched to Main internally by [GeckoSessionWrapper].
     */
    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(tab.id) {
            Log.i(TAG, "Creating wrapper tab=${tab.id} ws=${workspace.name}")
            val w = GeckoSessionWrapper(
                contextId  = workspace.contextId,
                appContext = context,
            )
            // loadUrl handles open() internally.
            // If URL is blank, just open the session.
            val url = tab.url.takeIf { it.isNotBlank() && it != "about:blank" }
            if (url != null) {
                w.loadUrl(url)   // open() + loadUri() in correct sequence on Main
            } else {
                w.open()         // just open, no URL
            }
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

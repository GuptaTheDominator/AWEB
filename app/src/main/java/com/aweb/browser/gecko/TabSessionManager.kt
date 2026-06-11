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
    companion object { private const val TAG = "TabSessionMgr" }
    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    fun get(tabId: String): GeckoSessionWrapper? = sessions[tabId]

    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(tab.id) {
            Log.i(TAG, "Creating session tab=${tab.id} ws=${workspace.name}")
            val w = GeckoSessionWrapper(contextId = workspace.contextId, appContext = context)
            w.open()
            val url = tab.url.takeIf { it.isNotBlank() && it != "about:blank" }
            if (url != null) w.loadUrl(url)
            w
        }
    }

    fun setActiveTab(activeTabId: String, allTabIds: List<String>) {
        allTabIds.forEach { id -> try { sessions[id]?.session?.setActive(id == activeTabId) } catch (e: Exception) { Log.w(TAG, "setActive $id: ${e.message}") } }
    }

    fun unload(tabId: String) { try { sessions.remove(tabId)?.close() } catch (e: Exception) { Log.w(TAG, "unload: ${e.message}") } }
    fun closeAllForWorkspace(ids: List<String>) { ids.forEach { id -> try { sessions.remove(id)?.close() } catch (_: Exception) {} } }
    fun closeAll() { sessions.values.forEach { try { it.close() } catch (_: Exception) {} }; sessions.clear() }
    val liveSessionCount: Int get() = sessions.size
}

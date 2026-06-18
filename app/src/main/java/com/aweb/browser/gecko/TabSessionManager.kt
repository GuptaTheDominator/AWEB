package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.browser.BrowserPermissionHandler
import com.aweb.browser.browser.DownloadHandler
import com.aweb.browser.browser.FileUploadHandler
import com.aweb.browser.browser.UserAgentManager
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps tabId -> [GeckoSessionWrapper].
 *
 * Thread safety: sessions map is guarded by [lock].
 * Called from both Main (TabViewModel) and IO (TabLifecycleManager, KeepAliveManager).
 */
@Singleton
class TabSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadHandler : DownloadHandler,
    private val permissionHandler: BrowserPermissionHandler,
    private val fileUploadHandler: FileUploadHandler,
) {
    companion object { private const val TAG = "TabSessionMgr" }

    private val lock     = Any()
    private val sessions = LinkedHashMap<String, GeckoSessionWrapper>()

    fun get(tabId: String): GeckoSessionWrapper? = synchronized(lock) { sessions[tabId] }

    fun getOrCreate(tab: TabEntity, workspace: WorkspaceEntity): GeckoSessionWrapper {
        synchronized(lock) {
            sessions[tab.id]?.let { return it }
        }
        Log.i(TAG, "Creating wrapper tab=${tab.id} ws=${workspace.name}")
        val uaMode = if (tab.userAgentMode == "desktop") UserAgentManager.UaMode.DESKTOP else UserAgentManager.UaMode.MOBILE
        val w = GeckoSessionWrapper(
            contextId = workspace.contextId,
            downloadHandler = downloadHandler,
            permissionHandler = permissionHandler,
            fileUploadHandler = fileUploadHandler,
            appContext = context,
            initialUaMode = uaMode,
        )
        // FIX (Bug 12): treat about:* and blank urls as "open empty session"
        val url = tab.url.takeIf { it.isNotBlank() && !it.startsWith("about:") }
        if (url != null) w.loadUrl(url) else w.open()
        synchronized(lock) {
            // Double-check: another thread may have created it while we were building
            sessions[tab.id]?.let {
                w.close()   // discard the one we just built
                return it
            }
            sessions[tab.id] = w
        }
        return w
    }

    fun setActiveTab(activeTabId: String, allTabIds: List<String>) {
        // Build a snapshot of (id, wrapper) pairs under the lock, then iterate outside
        val snapshot: List<Pair<String, GeckoSessionWrapper>> = synchronized(lock) {
            allTabIds.mapNotNull { id -> sessions[id]?.let { id to it } }
        }
        snapshot.forEach { (id, wrapper) ->
            try { wrapper.setActive(id == activeTabId) }
            catch (e: Exception) { Log.w(TAG, "setActive $id: ${e.message}") }
        }
    }

    fun setActive(tabId: String, active: Boolean) {
        val w = synchronized(lock) { sessions[tabId] }
        try { w?.setActive(active) } catch (e: Exception) { Log.w(TAG, "setActive: ${e.message}") }
    }

    fun unload(tabId: String) {
        val w = synchronized(lock) { sessions.remove(tabId) }
        try { w?.close() } catch (e: Exception) { Log.w(TAG, "unload: ${e.message}") }
    }

    fun closeAllForWorkspace(ids: List<String>) {
        val removed = synchronized(lock) { ids.mapNotNull { sessions.remove(it) } }
        removed.forEach { try { it.close() } catch (_: Exception) {} }
    }

    fun closeAll() {
        val all = synchronized(lock) { sessions.values.toList().also { sessions.clear() } }
        all.forEach { try { it.close() } catch (_: Exception) {} }
    }

    val liveSessionCount: Int get() = synchronized(lock) { sessions.size }
}

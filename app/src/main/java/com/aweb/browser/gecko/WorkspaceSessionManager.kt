package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object { private const val TAG = "WsSessionMgr" }
    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()
    fun getOrCreate(workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(workspace.id) {
            Log.i(TAG, "Creating ws session: ${workspace.name}")
            GeckoSessionWrapper(contextId = workspace.contextId, appContext = context).also { it.open() }
        }
    }
    fun get(wsId: String): GeckoSessionWrapper? = sessions[wsId]
    fun pauseAllExcept(activeId: String) { sessions.forEach { (id, w) -> if (id != activeId) try { w.session.setActive(false) } catch (_: Exception) {} } }
    fun resume(wsId: String) { try { sessions[wsId]?.session?.setActive(true) } catch (_: Exception) {} }
    fun closeAndRemove(wsId: String) { sessions.remove(wsId)?.close() }
    fun clearWorkspaceData(ws: WorkspaceEntity) { closeAndRemove(ws.id); getOrCreate(ws) }
    fun closeAll() { sessions.values.forEach { try { it.close() } catch (_: Exception) {} }; sessions.clear() }
}

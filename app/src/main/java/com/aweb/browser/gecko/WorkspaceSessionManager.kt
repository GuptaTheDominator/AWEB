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
    private val lock     = Any()
    private val sessions = LinkedHashMap<String, GeckoSessionWrapper>()

    fun getOrCreate(workspace: WorkspaceEntity): GeckoSessionWrapper =
        synchronized(lock) {
            sessions.getOrPut(workspace.id) {
                Log.i(TAG, "Creating wrapper ws=${workspace.name}")
                GeckoSessionWrapper(contextId = workspace.contextId, appContext = context)
                    .also { it.open() }
            }
        }

    fun get(wsId: String): GeckoSessionWrapper? = synchronized(lock) { sessions[wsId] }

    fun pauseAllExcept(activeId: String) {
        synchronized(lock) { sessions.filter { it.key != activeId }.values.toList() }
            .forEach { try { it.session?.setActive(false) } catch (_: Exception) {} }
    }

    fun resume(wsId: String) {
        try { synchronized(lock) { sessions[wsId] }?.session?.setActive(true) } catch (_: Exception) {}
    }

    fun closeAndRemove(wsId: String) {
        synchronized(lock) { sessions.remove(wsId) }?.close()
    }

    fun clearWorkspaceData(ws: WorkspaceEntity) { closeAndRemove(ws.id); getOrCreate(ws) }

    fun closeAll() {
        val all = synchronized(lock) { sessions.values.toList().also { sessions.clear() } }
        all.forEach { try { it.close() } catch (_: Exception) {} }
    }
}

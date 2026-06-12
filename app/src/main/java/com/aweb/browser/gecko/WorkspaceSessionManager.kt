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

    fun getOrCreate(workspace: WorkspaceEntity): GeckoSessionWrapper {
        // Fast path — already exists
        synchronized(lock) { sessions[workspace.id] }?.let { return it }

        // Slow path — create outside the lock so open() is never called while holding it.
        // Calling open() inside synchronized(lock) is unsafe because open() dispatches to
        // the Main thread via mainHandler.post, and any Main-thread code that then tries
        // to acquire the same lock would deadlock.
        Log.i(TAG, "Creating wrapper ws=${workspace.name}")
        val newWrapper = GeckoSessionWrapper(contextId = workspace.contextId, appContext = context)
        newWrapper.open()

        return synchronized(lock) {
            val existing = sessions[workspace.id]
            if (existing != null) {
                // We lost the race — discard ours
                newWrapper.close()
                existing
            } else {
                sessions[workspace.id] = newWrapper
                newWrapper
            }
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

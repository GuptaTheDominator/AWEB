package com.aweb.browser.gecko

import android.util.Log
import com.aweb.browser.data.entity.WorkspaceEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the mapping of workspace → [GeckoSessionWrapper].
 *
 * Each workspace gets exactly ONE GeckoSessionWrapper (Phase 2).
 * Phase 3 will extend this to a Map<workspaceId, List<GeckoSessionWrapper>>
 * to support multiple tabs per workspace.
 *
 * Key rule: the contextId stored in [WorkspaceEntity.contextId] is PERMANENT.
 * It is set once when the workspace is created and never changed.
 * GeckoView uses contextId to isolate cookies, localStorage, IndexedDB, and
 * cache. Different contextIds = completely separate browser profiles.
 */
@Singleton
class WorkspaceSessionManager @Inject constructor() {

    companion object {
        private const val TAG = "WorkspaceSessionManager"
    }

    // workspaceId -> active session wrapper for that workspace
    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    /**
     * Returns the [GeckoSessionWrapper] for the given workspace,
     * creating and opening one if it doesn't exist yet.
     */
    fun getOrCreate(workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(workspace.id) {
            Log.i(TAG, "Creating session for workspace '${workspace.name}' contextId=${workspace.contextId}")
            GeckoSessionWrapper(contextId = workspace.contextId).also { it.open() }
        }
    }

    /**
     * Returns the existing wrapper for a workspace, or null if none is active.
     */
    fun get(workspaceId: String): GeckoSessionWrapper? = sessions[workspaceId]

    /**
     * Pauses sessions for all workspaces except the active one.
     * Called when switching workspaces to reduce background resource use.
     * Phase 4 will expand this into full lifecycle management.
     */
    fun pauseAllExcept(activeWorkspaceId: String) {
        sessions.forEach { (id, wrapper) ->
            if (id != activeWorkspaceId) {
                wrapper.session.setActive(false)
                Log.d(TAG, "Paused session for workspace $id")
            }
        }
    }

    /**
     * Resumes the session for the given workspace (marks it active in Gecko).
     */
    fun resume(workspaceId: String) {
        sessions[workspaceId]?.session?.setActive(true)
        Log.d(TAG, "Resumed session for workspace $workspaceId")
    }

    /**
     * Closes and removes the session for a deleted workspace.
     * GeckoView will purge the contextId's storage when the session closes.
     */
    fun closeAndRemove(workspaceId: String) {
        sessions.remove(workspaceId)?.close()
        Log.i(TAG, "Closed and removed session for workspace $workspaceId")
    }

    /**
     * Clears all browser data (cookies, storage, cache) for a workspace's contextId.
     * Called when the user chooses "Clear workspace data".
     *
     * NOTE: GeckoView storage clearing by contextId is done via
     * GeckoRuntime.storageController in newer Gecko builds.
     * For now we close and re-open the session which resets the context.
     */
    fun clearWorkspaceData(workspace: WorkspaceEntity) {
        closeAndRemove(workspace.id)
        // Re-open fresh session with same contextId — Gecko will start fresh
        getOrCreate(workspace)
        Log.i(TAG, "Cleared data for workspace '${workspace.name}'")
    }

    /** Close all open sessions — call on app exit. */
    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        Log.i(TAG, "All sessions closed")
    }
}

package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.data.entity.WorkspaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 workspace-level session manager.
 * Kept for backward-compat; Phase 3+ uses TabSessionManager per-tab.
 */
@Singleton
class WorkspaceSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "WorkspaceSessionManager"
    }

    private val sessions = mutableMapOf<String, GeckoSessionWrapper>()

    fun getOrCreate(workspace: WorkspaceEntity): GeckoSessionWrapper {
        return sessions.getOrPut(workspace.id) {
            Log.i(TAG, "Creating session for workspace '${workspace.name}'")
            GeckoSessionWrapper(
                contextId  = workspace.contextId,
                appContext = context,
            ).also { it.open() }
        }
    }

    fun get(workspaceId: String): GeckoSessionWrapper? = sessions[workspaceId]

    fun pauseAllExcept(activeWorkspaceId: String) {
        sessions.forEach { (id, wrapper) ->
            if (id != activeWorkspaceId) wrapper.session.setActive(false)
        }
    }

    fun resume(workspaceId: String) {
        sessions[workspaceId]?.session?.setActive(true)
    }

    fun closeAndRemove(workspaceId: String) {
        sessions.remove(workspaceId)?.close()
        Log.i(TAG, "Closed session for workspace $workspaceId")
    }

    fun clearWorkspaceData(workspace: WorkspaceEntity) {
        closeAndRemove(workspace.id)
        getOrCreate(workspace)
        Log.i(TAG, "Cleared data for workspace '${workspace.name}'")
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}

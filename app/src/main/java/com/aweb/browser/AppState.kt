package com.aweb.browser

import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity

/**
 * Lightweight in-process snapshot store.
 *
 * Updated by [TabViewModel] and [WorkspaceViewModel] after every state change.
 * Read by [MemoryPressureReceiver] (which runs on any thread) to get the
 * current tab/workspace lists without needing coroutines or DI.
 *
 * All writes are @Volatile — safe for single-writer / multi-reader use.
 */
object AppState {

    @Volatile var currentTabs      : List<TabEntity>    = emptyList()
    @Volatile var currentWorkspace : WorkspaceEntity?   = null

    fun update(workspace: WorkspaceEntity?, tabs: List<TabEntity>) {
        currentWorkspace = workspace
        currentTabs      = tabs
    }
}

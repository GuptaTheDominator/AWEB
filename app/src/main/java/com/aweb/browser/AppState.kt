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
 * Uses a single AtomicReference<Snapshot> so readers always see a consistent
 * pair of (workspace, tabs) — previously two separate @Volatile fields could
 * be observed in a torn state between writes.
 */
object AppState {

    data class Snapshot(
        val workspace: WorkspaceEntity?,
        val tabs     : List<TabEntity>,
    )

    private val _snapshot = java.util.concurrent.atomic.AtomicReference(
        Snapshot(workspace = null, tabs = emptyList())
    )

    val currentWorkspace: WorkspaceEntity? get() = _snapshot.get().workspace
    val currentTabs     : List<TabEntity>  get() = _snapshot.get().tabs

    fun update(workspace: WorkspaceEntity?, tabs: List<TabEntity>) {
        _snapshot.set(Snapshot(workspace = workspace, tabs = tabs))
    }
}

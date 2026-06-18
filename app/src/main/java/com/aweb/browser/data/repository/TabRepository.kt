package com.aweb.browser.data.repository

import com.aweb.browser.data.db.TabDao
import com.aweb.browser.data.entity.TabEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for tab data.
 *
 * All tab mutations go through here — DAOs are never called directly from ViewModels.
 *
 * Tab ordering: [TabEntity.orderIndex] is 0-based within a workspace.
 * Pinned tabs are always sorted before unpinned tabs in the UI (TabStrip handles display order).
 */
@Singleton
class TabRepository @Inject constructor(
    private val dao: TabDao,
) {

    fun observeAllTabs(): Flow<List<TabEntity>> = dao.observeAll()

    fun observeTabsForWorkspace(workspaceId: String): Flow<List<TabEntity>> =
        dao.observeByWorkspace(workspaceId)

    suspend fun getTabsForWorkspace(workspaceId: String): List<TabEntity> =
        dao.getByWorkspace(workspaceId)

    suspend fun getById(tabId: String): TabEntity? = dao.getById(tabId)

    suspend fun getActiveTab(workspaceId: String): TabEntity? = dao.getActiveTab(workspaceId)

    /**
     * Creates a new tab at the end of the workspace tab list.
     * Does NOT make it active — caller must call [setActiveTab] separately.
     */
    suspend fun createTab(
        workspaceId : String,
        url         : String = "https://duckduckgo.com",
        title       : String = "New Tab",
        isPinned    : Boolean = false,
    ): TabEntity {
        val now      = System.currentTimeMillis()
        val existing = dao.getByWorkspace(workspaceId)
        val tab      = TabEntity(
            id                 = UUID.randomUUID().toString(),
            workspaceId        = workspaceId,
            url                = url,
            title              = title,
            orderIndex         = existing.size,
            isActive           = false,
            isPinned           = isPinned,
            keepAlive          = false,
            lastLifecycleState = "unloaded",
            lastAccessed       = now,
            createdAt          = now,
            updatedAt          = now,
        )
        dao.insert(tab)
        return tab
    }

    suspend fun setActiveTab(workspaceId: String, tabId: String) {
        // Single @Transaction call — atomically clears old active flag and sets new one.
        // Previously these were two separate calls with no transaction, creating a window
        // where the DB had zero active tabs (causing safeCollect to seed a duplicate tab).
        dao.setActive(workspaceId, tabId)
    }

    suspend fun updateTitleAndUrl(tabId: String, title: String, url: String) {
        dao.updateTitleAndUrl(tabId, title, url)
    }

    suspend fun updateLifecycleState(tabId: String, state: String) {
        dao.updateLifecycleState(tabId, state)
    }

    suspend fun updateUserAgentMode(tabId: String, mode: String) {
        dao.updateUserAgentMode(tabId, mode)
    }

    suspend fun setKeepAlive(tabId: String, keepAlive: Boolean) {
        dao.setKeepAlive(tabId, keepAlive)
    }

    suspend fun setPinned(tabId: String, pinned: Boolean) {
        val tab = dao.getById(tabId) ?: return
        dao.update(tab.copy(isPinned = pinned, updatedAt = System.currentTimeMillis()))
    }

    suspend fun closeTab(tabId: String) {
        val tab = dao.getById(tabId) ?: return
        dao.delete(tab)
        // Re-compact order indices for remaining tabs in this workspace
        recompactOrder(tab.workspaceId)
    }

    suspend fun closeAllTabsInWorkspace(workspaceId: String) {
        dao.deleteAllInWorkspace(workspaceId)
    }

    /**
     * Reorder tabs after drag-and-drop.
     * [orderedTabIds] is the new order of tab IDs within the workspace.
     */
    suspend fun reorder(workspaceId: String, orderedTabIds: List<String>) {
        val now  = System.currentTimeMillis()
        val tabs = dao.getByWorkspace(workspaceId).associateBy { it.id }
        orderedTabIds.forEachIndexed { index, id ->
            tabs[id]?.let { tab ->
                dao.update(tab.copy(orderIndex = index, updatedAt = now))
            }
        }
    }

    private suspend fun recompactOrder(workspaceId: String) {
        val now  = System.currentTimeMillis()
        dao.getByWorkspace(workspaceId).forEachIndexed { index, tab ->
            if (tab.orderIndex != index) {
                dao.update(tab.copy(orderIndex = index, updatedAt = now))
            }
        }
    }

    /**
     * Ensures at least one tab exists in a workspace.
     * FIX (Bug 12): New blank tab uses "about:newtab" so GeckoSessionWrapper
     * treats it as a blank session (no URL to navigate to), avoiding an unwanted
     * automatic DuckDuckGo navigation on every new-tab open.
     */
    suspend fun ensureAtLeastOneTab(
        workspaceId: String,
        homepage: String = "about:newtab",
    ): TabEntity {
        val tabs = dao.getByWorkspace(workspaceId)
        if (tabs.isNotEmpty()) return tabs.first()
        val tab = createTab(workspaceId = workspaceId, url = homepage, title = "New Tab")
        setActiveTab(workspaceId, tab.id)
        return tab
    }
}

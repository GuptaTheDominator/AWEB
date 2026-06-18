package com.aweb.browser.data.repository

import com.aweb.browser.data.db.WorkspaceDao
import com.aweb.browser.data.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for workspace data.
 *
 * All workspace mutations go through here so the DAO is never called
 * directly from ViewModels.
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    private val dao: WorkspaceDao
) {

    /** Live list of all workspaces ordered by [WorkspaceEntity.orderIndex]. */
    val workspaces: Flow<List<WorkspaceEntity>> = dao.observeAll()

    suspend fun getAll(): List<WorkspaceEntity> = dao.getAll()

    suspend fun getById(id: String): WorkspaceEntity? = dao.getById(id)

    suspend fun getActive(): WorkspaceEntity? = dao.getActive()

    /**
     * Creates a new workspace with a fresh UUID and a unique GeckoView contextId.
     * The contextId is permanent — it must never change after creation because
     * GeckoView ties cookie/storage isolation to it.
     */
    suspend fun createWorkspace(
        name     : String,
        colorHex : String  = DEFAULT_COLORS.random(),
        iconName : String? = null,
    ): WorkspaceEntity {
        val now      = System.currentTimeMillis()
        val allCount = dao.getAll().size
        val entity   = WorkspaceEntity(
            id         = UUID.randomUUID().toString(),
            name       = name,
            contextId  = "aweb_ws_${UUID.randomUUID()}",   // permanent isolation key
            colorHex   = colorHex,
            iconName   = iconName,
            orderIndex = allCount,
            isActive   = false,
            createdAt  = now,
            updatedAt  = now,
        )
        dao.insert(entity)
        return entity
    }

    suspend fun renameWorkspace(id: String, newName: String) {
        val ws = dao.getById(id) ?: return
        dao.update(ws.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateWorkspace(entity: WorkspaceEntity) {
        dao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteWorkspace(id: String) {
        val ws = dao.getById(id) ?: return
        dao.delete(ws)
        recompactOrder()
    }

    suspend fun switchActive(id: String) {
        dao.switchActive(id)
    }

    /** Reorder workspaces after drag-and-drop. Caller supplies the new ordered list of IDs. */
    suspend fun reorder(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            val ws = dao.getById(id) ?: return@forEachIndexed
            dao.update(ws.copy(orderIndex = index, updatedAt = now))
        }
    }

    private suspend fun recompactOrder() {
        val now = System.currentTimeMillis()
        dao.getAll().forEachIndexed { index, ws ->
            if (ws.orderIndex != index) {
                dao.update(ws.copy(orderIndex = index, updatedAt = now))
            }
        }
    }

    /**
     * Ensures at least one workspace exists (the default "Personal" workspace).
     * Called on first launch.
     */
    suspend fun ensureDefaultWorkspace() {
        if (dao.getAll().isEmpty()) {
            val ws = createWorkspace(name = "Personal", colorHex = "#2F8CFF")
            dao.switchActive(ws.id)
        }
    }

    companion object {
        val DEFAULT_COLORS = listOf(
            "#2F8CFF",  // AWEB blue
            "#22C7A9",  // teal
            "#4DD8FF",  // cyan
            "#FFC857",  // amber
            "#FF5C7A",  // rose
            "#7CB7FF",  // soft blue
            "#63D471",  // success green
        )
    }
}

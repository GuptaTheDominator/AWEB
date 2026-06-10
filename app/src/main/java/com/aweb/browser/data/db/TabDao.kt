package com.aweb.browser.data.db

import androidx.room.*
import com.aweb.browser.data.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {

    @Query("SELECT * FROM tabs WHERE workspace_id = :workspaceId ORDER BY order_index ASC")
    fun observeByWorkspace(workspaceId: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE workspace_id = :workspaceId ORDER BY order_index ASC")
    suspend fun getByWorkspace(workspaceId: String): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TabEntity?

    @Query("SELECT * FROM tabs WHERE workspace_id = :workspaceId AND is_active = 1 LIMIT 1")
    suspend fun getActiveTab(workspaceId: String): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: TabEntity)

    @Update
    suspend fun update(tab: TabEntity)

    @Delete
    suspend fun delete(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE workspace_id = :workspaceId")
    suspend fun deleteAllInWorkspace(workspaceId: String)

    @Query("UPDATE tabs SET is_active = 0 WHERE workspace_id = :workspaceId")
    suspend fun clearActiveFlags(workspaceId: String)

    @Query("""
        UPDATE tabs SET is_active = 1, last_accessed = :now, updated_at = :now,
                        last_lifecycle_state = 'active'
        WHERE id = :tabId
    """)
    suspend fun setActive(tabId: String, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE tabs SET keep_alive = :keepAlive, updated_at = :now WHERE id = :tabId
    """)
    suspend fun setKeepAlive(tabId: String, keepAlive: Boolean, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE tabs SET last_lifecycle_state = :state, updated_at = :now WHERE id = :tabId
    """)
    suspend fun updateLifecycleState(tabId: String, state: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tabs SET title = :title, url = :url, updated_at = :now WHERE id = :tabId")
    suspend fun updateTitleAndUrl(tabId: String, title: String, url: String, now: Long = System.currentTimeMillis())
}

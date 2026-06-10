package com.aweb.browser.data.db

import androidx.room.*
import com.aweb.browser.data.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspaces ORDER BY order_index ASC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY order_index ASC")
    suspend fun getAll(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Delete
    suspend fun delete(workspace: WorkspaceEntity)

    @Query("UPDATE workspaces SET is_active = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE workspaces SET is_active = 1, updated_at = :now WHERE id = :id")
    suspend fun setActive(id: String, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun switchActive(id: String) {
        clearActiveFlags()
        setActive(id)
    }
}

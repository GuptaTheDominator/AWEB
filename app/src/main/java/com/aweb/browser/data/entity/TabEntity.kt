package com.aweb.browser.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent tab record.
 *
 * Maps to the `tabs` table defined in the plan (Section 16).
 *
 * [lastLifecycleState] is one of: "active", "recent", "unloaded", "keep_alive".
 * The live state is managed in memory by TabLifecycleManager (Phase 4).
 * This field records the last known state for restoration after app restart.
 */
@Entity(
    tableName    = "tabs",
    foreignKeys  = [
        ForeignKey(
            entity        = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns  = ["workspace_id"],
            onDelete      = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("workspace_id"), Index("workspace_id", "is_active"), Index("workspace_id", "order_index")]
)
data class TabEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                         // UUID

    @ColumnInfo(name = "workspace_id")
    val workspaceId: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "keep_alive")
    val keepAlive: Boolean = false,

    @ColumnInfo(name = "last_lifecycle_state")
    val lastLifecycleState: String = "unloaded",

    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Long,

    @ColumnInfo(name = "user_agent_mode")
    val userAgentMode: String = "mobile",   // "mobile" or "desktop"

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

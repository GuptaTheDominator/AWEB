package com.aweb.browser.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent workspace record.
 *
 * Maps to the `workspaces` table defined in the plan (Section 16).
 *
 * [contextId] is the GeckoView session context ID for this workspace.
 * It is set once on creation and never changed — changing it would
 * destroy the workspace's cookie/storage isolation.
 */
@Entity(
    tableName = "workspaces",
    indices   = [Index(value = ["context_id"], unique = true)]
)
data class WorkspaceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                      // UUID

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "context_id")
    val contextId: String,               // permanent workspace isolation key

    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,        // e.g. "#2F8CFF"

    @ColumnInfo(name = "icon_name")
    val iconName: String? = null,        // material icon name

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

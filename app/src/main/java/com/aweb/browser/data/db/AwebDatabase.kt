package com.aweb.browser.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aweb.browser.data.entity.AppSettingEntity
import com.aweb.browser.data.entity.BookmarkEntity
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity

/**
 * Room database — version 3 adds [BookmarkEntity] and per-tab user-agent mode.
 * Production migrations are registered in [DatabaseModule].
 */
@Database(
    entities     = [
        WorkspaceEntity::class,
        TabEntity::class,
        AppSettingEntity::class,
        BookmarkEntity::class,
    ],
    version      = 3,
    exportSchema = false,
)
abstract class AwebDatabase : RoomDatabase() {
    abstract fun workspaceDao() : WorkspaceDao
    abstract fun tabDao()       : TabDao
    abstract fun settingDao()   : AppSettingDao
    abstract fun bookmarkDao()  : BookmarkDao
}

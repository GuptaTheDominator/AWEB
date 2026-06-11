package com.aweb.browser.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aweb.browser.data.entity.AppSettingEntity
import com.aweb.browser.data.entity.BookmarkEntity
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity

/**
 * Room database — version 2 adds [BookmarkEntity].
 * fallbackToDestructiveMigration() is in [DatabaseModule] for dev builds.
 * Replace with a proper migration before signing the final APK.
 */
@Database(
    entities     = [
        WorkspaceEntity::class,
        TabEntity::class,
        AppSettingEntity::class,
        BookmarkEntity::class,
    ],
    version      = 2,
    exportSchema = true,
)
abstract class AwebDatabase : RoomDatabase() {
    abstract fun workspaceDao() : WorkspaceDao
    abstract fun tabDao()       : TabDao
    abstract fun settingDao()   : AppSettingDao
    abstract fun bookmarkDao()  : BookmarkDao
}

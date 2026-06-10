package com.aweb.browser.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aweb.browser.data.entity.AppSettingEntity
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity

/**
 * Room database for AWEB.
 *
 * Phase 1: schema defined, no DAOs wired to UI yet.
 * Phase 2: WorkspaceDao used.
 * Phase 3: TabDao used.
 */
@Database(
    entities = [
        WorkspaceEntity::class,
        TabEntity::class,
        AppSettingEntity::class,
    ],
    version  = 1,
    exportSchema = true,
)
abstract class AwebDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun tabDao()      : TabDao
    abstract fun settingDao()  : AppSettingDao
}

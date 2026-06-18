package com.aweb.browser.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aweb.browser.data.db.*
import com.aweb.browser.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id TEXT NOT NULL PRIMARY KEY,
                    url TEXT NOT NULL,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_url ON bookmarks(url)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tabs ADD COLUMN user_agent_mode TEXT NOT NULL DEFAULT 'mobile'")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Deduplicate bookmarks before making url unique. Keep the oldest row per URL.
            db.execSQL("DELETE FROM bookmarks WHERE rowid NOT IN (SELECT MIN(rowid) FROM bookmarks GROUP BY url)")
            db.execSQL("DROP INDEX IF EXISTS index_bookmarks_url")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_url ON bookmarks(url)")

            // Runtime queries frequently filter tabs by workspace + active/order state.
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_workspace_id_is_active ON tabs(workspace_id, is_active)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_workspace_id_order_index ON tabs(workspace_id, order_index)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AwebDatabase =
        Room.databaseBuilder(context, AwebDatabase::class.java, "aweb.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    // ── DAOs ──────────────────────────────────────────────────────────────
    @Provides fun provideWorkspaceDao(db: AwebDatabase): WorkspaceDao  = db.workspaceDao()
    @Provides fun provideTabDao      (db: AwebDatabase): TabDao        = db.tabDao()
    @Provides fun provideSettingDao  (db: AwebDatabase): AppSettingDao = db.settingDao()
    @Provides fun provideBookmarkDao (db: AwebDatabase): BookmarkDao   = db.bookmarkDao()

    // ── Repositories ──────────────────────────────────────────────────────
    @Provides @Singleton
    fun provideWorkspaceRepository(dao: WorkspaceDao)  = WorkspaceRepository(dao)

    @Provides @Singleton
    fun provideTabRepository(dao: TabDao)               = TabRepository(dao)

    @Provides @Singleton
    fun provideSettingsRepository(dao: AppSettingDao)  = SettingsRepository(dao)

    @Provides @Singleton
    fun provideBookmarkRepository(dao: BookmarkDao)    = BookmarkRepository(dao)
}

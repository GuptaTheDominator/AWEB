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
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_url ON bookmarks(url)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tabs ADD COLUMN user_agent_mode TEXT NOT NULL DEFAULT 'mobile'")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AwebDatabase =
        Room.databaseBuilder(context, AwebDatabase::class.java, "aweb.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

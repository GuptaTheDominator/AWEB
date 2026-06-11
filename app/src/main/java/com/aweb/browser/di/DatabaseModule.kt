package com.aweb.browser.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AwebDatabase =
        Room.databaseBuilder(context, AwebDatabase::class.java, "aweb.db")
            .fallbackToDestructiveMigration()
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

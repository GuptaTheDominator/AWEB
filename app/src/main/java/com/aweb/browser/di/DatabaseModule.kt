package com.aweb.browser.di

import android.content.Context
import androidx.room.Room
import com.aweb.browser.data.db.*
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
        Room.databaseBuilder(
            context,
            AwebDatabase::class.java,
            "aweb.db"
        )
            .fallbackToDestructiveMigration()   // replace with proper migrations before v1 release
            .build()

    @Provides
    fun provideWorkspaceDao(db: AwebDatabase): WorkspaceDao = db.workspaceDao()

    @Provides
    fun provideTabDao(db: AwebDatabase): TabDao = db.tabDao()

    @Provides
    fun provideSettingDao(db: AwebDatabase): AppSettingDao = db.settingDao()
}

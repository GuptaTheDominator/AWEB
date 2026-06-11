package com.aweb.browser.di

import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.ui.keepalive.KeepAliveManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LifecycleModule {

    @Provides
    @Singleton
    fun provideTabLifecycleManager(
        tabRepo       : TabRepository,
        sessionManager: TabSessionManager,
    ): TabLifecycleManager = TabLifecycleManager(tabRepo, sessionManager)

    @Provides
    @Singleton
    fun provideKeepAliveManager(
        tabRepo         : TabRepository,
        sessionManager  : TabSessionManager,
        lifecycleManager: TabLifecycleManager,
    ): KeepAliveManager = KeepAliveManager(tabRepo, sessionManager, lifecycleManager)
}

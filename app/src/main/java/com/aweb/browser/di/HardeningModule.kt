package com.aweb.browser.di

import com.aweb.browser.crash.CrashRecoveryManager
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.lifecycle.TabLifecycleManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HardeningModule {

    @Provides
    @Singleton
    fun provideCrashRecoveryManager(
        @ApplicationContext context: Context,
        workspaceRepo : WorkspaceRepository,
        tabRepo       : TabRepository,
        lifecycleMgr  : TabLifecycleManager,
    ): CrashRecoveryManager = CrashRecoveryManager(context, workspaceRepo, tabRepo, lifecycleMgr)
}

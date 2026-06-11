package com.aweb.browser.di

import com.aweb.browser.browser.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrowserModule {

    @Provides @Singleton fun provideDownloadHandler()    = DownloadHandler()
    @Provides @Singleton fun providePermissionHandler()  = BrowserPermissionHandler()
    @Provides @Singleton fun provideFileUploadHandler()  = FileUploadHandler()
    @Provides @Singleton fun provideFullscreenHandler()  = FullscreenHandler()
    @Provides @Singleton fun provideFindInPageHandler()  = FindInPageHandler()
    @Provides @Singleton fun provideUserAgentManager()   = UserAgentManager()
}

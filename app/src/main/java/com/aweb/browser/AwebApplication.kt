package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aweb.browser.gecko.GeckoRuntimeManager
import com.aweb.browser.lifecycle.MemoryPressureReceiver
import com.aweb.browser.lifecycle.TabLifecycleManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry-point.
 *
 * Phase 4 additions:
 *  - Injects [TabLifecycleManager] (Hilt singleton)
 *  - Registers [MemoryPressureReceiver] with the system
 *
 * The receiver needs live tab/workspace snapshots; these are provided
 * by [AppState] which is a lightweight in-process singleton updated
 * by TabViewModel whenever the state changes.
 */
@HiltAndroidApp
class AwebApplication : Application() {

    @Inject lateinit var lifecycleManager: TabLifecycleManager

    override fun onCreate() {
        super.onCreate()

        // Warm up GeckoView runtime before any Activity draws
        GeckoRuntimeManager.init(this)

        // Register Android memory pressure callbacks
        registerComponentCallbacks(
            MemoryPressureReceiver(
                lifecycleManager  = lifecycleManager,
                tabsSnapshot      = { AppState.currentTabs },
                workspaceSnapshot = { AppState.currentWorkspace },
            )
        )

        // Register foreground service notification channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps AWEB workspaces and Keep Alive tabs running"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}

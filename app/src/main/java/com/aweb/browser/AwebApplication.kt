package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.aweb.browser.gecko.GeckoRuntimeManager
import com.aweb.browser.lifecycle.MemoryPressureReceiver
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.service.ServiceHealthWorker
import com.aweb.browser.service.ServiceManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry-point.
 *
 * Phase 7 additions:
 *  - Implements [Configuration.Provider] so Hilt can inject into WorkManager workers.
 *  - Starts [AwebForegroundService] on app creation via [ServiceManager].
 *  - Schedules [ServiceHealthWorker] via WorkManager.
 *  - Registers [MemoryPressureReceiver] with the system.
 */
@HiltAndroidApp
class AwebApplication : Application(), Configuration.Provider {

    @Inject lateinit var lifecycleManager : TabLifecycleManager
    @Inject lateinit var serviceManager   : ServiceManager
    @Inject lateinit var workerFactory    : HiltWorkerFactory

    // Required by Configuration.Provider for Hilt + WorkManager integration
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1. Warm up GeckoView runtime before any Activity draws
        GeckoRuntimeManager.init(this)

        // 2. Register Android memory pressure callbacks
        registerComponentCallbacks(
            MemoryPressureReceiver(
                lifecycleManager  = lifecycleManager,
                tabsSnapshot      = { AppState.currentTabs },
                workspaceSnapshot = { AppState.currentWorkspace },
            )
        )

        // 3. Register foreground service notification channel
        createNotificationChannel()

        // 4. Start foreground service — elevates process priority immediately
        serviceManager.startService(this)

        // 5. Schedule periodic WorkManager health check
        ServiceHealthWorker.schedule(this)
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

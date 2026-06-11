package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
 * IMPORTANT: Keep onCreate() as fast as possible.
 * Heavy singletons (GeckoRuntime, WorkManager, foreground service) are
 * initialised after the first frame so the launcher doesn't ANR.
 */
@HiltAndroidApp
class AwebApplication : Application(), Configuration.Provider {

    @Inject lateinit var lifecycleManager : TabLifecycleManager
    @Inject lateinit var serviceManager   : ServiceManager
    @Inject lateinit var workerFactory    : HiltWorkerFactory

    // WorkManager configuration — must be provided before WorkManager.getInstance()
    // is called. Returning this here disables auto-init (removed via manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1. Register notification channel immediately (needed before service starts)
        createNotificationChannel()

        // 2. Register memory pressure callbacks (lightweight, main-thread safe)
        registerComponentCallbacks(
            MemoryPressureReceiver(
                lifecycleManager  = lifecycleManager,
                tabsSnapshot      = { AppState.currentTabs },
                workspaceSnapshot = { AppState.currentWorkspace },
            )
        )

        // 3. Defer all heavy startup work until after the first frame renders.
        //    This prevents the launcher from showing a black screen while
        //    GeckoRuntime spins up its internal Gecko process.
        Handler(Looper.getMainLooper()).post {
            initGeckoRuntime()
            startForegroundServiceSafely()
            scheduleHealthWorkerSafely()
        }
    }

    private fun initGeckoRuntime() {
        try {
            GeckoRuntimeManager.init(this)
        } catch (e: Exception) {
            android.util.Log.e("AwebApplication", "GeckoRuntime init failed", e)
        }
    }

    private fun startForegroundServiceSafely() {
        try {
            serviceManager.startService(this)
        } catch (e: Exception) {
            // On some devices startForegroundService can throw if the app
            // moves to background very quickly. Non-fatal — WorkManager will
            // restart the service on the next health check.
            android.util.Log.w("AwebApplication", "Service start deferred: ${e.message}")
        }
    }

    private fun scheduleHealthWorkerSafely() {
        try {
            ServiceHealthWorker.schedule(this)
        } catch (e: Exception) {
            android.util.Log.w("AwebApplication", "WorkManager schedule failed: ${e.message}")
        }
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

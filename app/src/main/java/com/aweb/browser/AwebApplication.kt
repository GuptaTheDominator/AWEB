package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
 * CRITICAL rules:
 *  1. GeckoRuntime.create() MUST run on the MAIN THREAD — not IO, not Default.
 *  2. Defer it with Handler.post so the first Activity frame renders first.
 *  3. Every step is individually try-catched so one failure never kills the app.
 */
@HiltAndroidApp
class AwebApplication : Application(), Configuration.Provider {

    @Inject lateinit var lifecycleManager : TabLifecycleManager
    @Inject lateinit var serviceManager   : ServiceManager
    @Inject lateinit var workerFactory    : HiltWorkerFactory

    private val mainHandler = Handler(Looper.getMainLooper())

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i("AwebApplication", "onCreate — Hilt injected, starting deferred init")

        // Install global crash logger immediately
        installExceptionLogger()

        // Notification channel must exist before the foreground service starts
        createNotificationChannel()

        // Register memory pressure callbacks (lightweight, safe on Main)
        try {
            registerComponentCallbacks(
                MemoryPressureReceiver(
                    lifecycleManager  = lifecycleManager,
                    tabsSnapshot      = { AppState.currentTabs },
                    workspaceSnapshot = { AppState.currentWorkspace },
                )
            )
        } catch (e: Exception) {
            Log.e("AwebApplication", "registerComponentCallbacks: ${e.message}")
        }

        // Defer heavy startup to after first frame — but stay on MAIN THREAD
        // GeckoRuntime.create() requires Main thread, so we use Handler not coroutines
        mainHandler.post {
            // Step 1: init GeckoRuntime on Main thread (GeckoView requirement)
            try {
                GeckoRuntimeManager.init(applicationContext)
            } catch (e: Exception) {
                Log.e("AwebApplication", "GeckoRuntime init failed: ${e.message}", e)
                // Retry once after 1 second
                mainHandler.postDelayed({
                    try { GeckoRuntimeManager.init(applicationContext) }
                    catch (e2: Exception) { Log.e("AwebApplication", "GeckoRuntime retry failed: ${e2.message}") }
                }, 1000)
            }

            // Step 2: start foreground service
            try {
                serviceManager.startService(applicationContext)
            } catch (e: Exception) {
                Log.w("AwebApplication", "Service start: ${e.message}")
            }

            // Step 3: schedule WorkManager (on IO internally, safe to call from Main)
            mainHandler.postDelayed({
                try {
                    ServiceHealthWorker.schedule(applicationContext)
                } catch (e: Exception) {
                    Log.w("AwebApplication", "WorkManager schedule: ${e.message}")
                }
            }, 2000) // extra delay so WorkManager is fully ready
        }
    }

    private fun installExceptionLogger() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AWEB_CRASH", "══════════════════════════════════════")
            Log.e("AWEB_CRASH", "CRASH on thread: ${thread.name}")
            Log.e("AWEB_CRASH", "${throwable.javaClass.name}: ${throwable.message}")
            throwable.stackTrace.take(30).forEach { Log.e("AWEB_CRASH", "  at $it") }
            throwable.cause?.let { c ->
                Log.e("AWEB_CRASH", "Caused by: ${c.javaClass.name}: ${c.message}")
                c.stackTrace.take(15).forEach { Log.e("AWEB_CRASH", "  at $it") }
            }
            Log.e("AWEB_CRASH", "══════════════════════════════════════")
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
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
            } catch (e: Exception) {
                Log.e("AwebApplication", "createNotificationChannel: ${e.message}")
            }
        }
    }
}

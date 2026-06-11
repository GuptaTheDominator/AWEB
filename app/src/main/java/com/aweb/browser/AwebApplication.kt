package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.aweb.browser.gecko.GeckoRuntimeManager
import com.aweb.browser.lifecycle.MemoryPressureReceiver
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.service.ServiceHealthWorker
import com.aweb.browser.service.ServiceManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry-point.
 *
 * Startup rules:
 *  1. Keep onCreate() synchronous and fast — only lightweight ops allowed.
 *  2. GeckoRuntime.create() is slow — launch on IO dispatcher with a delay
 *     so Compose draws the first frame before the engine starts.
 *  3. Foreground service is started after GeckoRuntime is ready.
 *  4. Every operation is wrapped in try-catch — one failure must NOT
 *     propagate to crash the whole app.
 */
@HiltAndroidApp
class AwebApplication : Application(), Configuration.Provider {

    @Inject lateinit var lifecycleManager : TabLifecycleManager
    @Inject lateinit var serviceManager   : ServiceManager
    @Inject lateinit var workerFactory    : HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i("AwebApplication", "onCreate start")

        // Install a global exception logger FIRST so we always see the crash
        installExceptionLogger()

        // Register notification channel (must be before service starts — lightweight)
        createNotificationChannel()

        // Register memory pressure callbacks (lightweight, main-thread safe)
        try {
            registerComponentCallbacks(
                MemoryPressureReceiver(
                    lifecycleManager  = lifecycleManager,
                    tabsSnapshot      = { AppState.currentTabs },
                    workspaceSnapshot = { AppState.currentWorkspace },
                )
            )
        } catch (e: Exception) {
            Log.e("AwebApplication", "registerComponentCallbacks failed: ${e.message}")
        }

        // Defer heavy work to background — GeckoRuntime.create() blocks ~300-500ms
        appScope.launch {
            // Small delay so the UI thread draws the first frame first
            delay(500)

            // Step 1: init GeckoView runtime
            try {
                GeckoRuntimeManager.init(applicationContext)
                Log.i("AwebApplication", "GeckoRuntime ready")
            } catch (e: Exception) {
                Log.e("AwebApplication", "GeckoRuntime init FAILED: ${e.message}", e)
                // Do not crash — sessions will retry lazily via getOrInit()
            }

            // Step 2: start foreground service (needs to happen from any thread is OK)
            try {
                serviceManager.startService(applicationContext)
                Log.i("AwebApplication", "Foreground service started")
            } catch (e: Exception) {
                Log.w("AwebApplication", "Service start failed: ${e.message}")
            }

            // Step 3: schedule WorkManager health check
            try {
                ServiceHealthWorker.schedule(applicationContext)
                Log.i("AwebApplication", "Health worker scheduled")
            } catch (e: Exception) {
                Log.w("AwebApplication", "WorkManager schedule failed: ${e.message}")
            }
        }

        Log.i("AwebApplication", "onCreate complete")
    }

    /**
     * Install a global uncaught exception logger.
     * This logs every crash to logcat with full stack trace so it's visible
     * even without adb by reading /data/anr/ or crash logs in Settings.
     * Always chains to the default handler so Android shows the crash dialog.
     */
    private fun installExceptionLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AWEB_CRASH", "═══════════════════════════════════════")
            Log.e("AWEB_CRASH", "UNCAUGHT EXCEPTION on thread: ${thread.name}")
            Log.e("AWEB_CRASH", "Exception: ${throwable.javaClass.name}: ${throwable.message}")
            throwable.stackTrace.take(20).forEach { frame ->
                Log.e("AWEB_CRASH", "  at $frame")
            }
            throwable.cause?.let { cause ->
                Log.e("AWEB_CRASH", "Caused by: ${cause.javaClass.name}: ${cause.message}")
                cause.stackTrace.take(10).forEach { frame ->
                    Log.e("AWEB_CRASH", "  at $frame")
                }
            }
            Log.e("AWEB_CRASH", "═══════════════════════════════════════")

            // Write crash info to DataStore safely
            try {
                appScope.launch {
                    try {
                        // Just log — don't depend on CrashRecoveryManager here
                        // because it may not be ready if crash is in DI graph
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
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

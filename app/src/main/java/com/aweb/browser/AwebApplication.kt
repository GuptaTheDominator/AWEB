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

@HiltAndroidApp
class AwebApplication : Application(), Configuration.Provider {
    @Inject lateinit var lifecycleManager: TabLifecycleManager
    @Inject lateinit var serviceManager: ServiceManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    private val mainHandler = Handler(Looper.getMainLooper())

    override val workManagerConfiguration: Configuration
        get() = try {
            Configuration.Builder().setWorkerFactory(workerFactory).setMinimumLoggingLevel(Log.INFO).build()
        } catch (e: UninitializedPropertyAccessException) {
            Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
        }

    override fun onCreate() {
        super.onCreate()
        installExceptionLogger()
        createNotificationChannel()

        // Start foreground service IMMEDIATELY and SYNCHRONOUSLY so the process
        // gets elevated oom_score_adj BEFORE LMKD can kill us.
        // From the logcat: AWEB was killed with oom_score_adj=915 (background)
        // because the foreground service was not yet running.
        // A foreground service sets oom_score_adj to ~100, making LMKD skip us.
        try {
            serviceManager.startService(this)
            Log.i("AwebApp", "Foreground service started — process priority elevated")
        } catch (e: Exception) {
            Log.e("AwebApp", "Service start failed: ${e.message}")
        }

        // Register memory pressure callbacks
        try {
            registerComponentCallbacks(MemoryPressureReceiver(
                lifecycleManager, { AppState.currentTabs }, { AppState.currentWorkspace }))
        } catch (e: Exception) { Log.e("AwebApp", "callbacks: ${e.message}") }

        // GeckoRuntime.create() MUST run on the Main thread.
        // Post after current frame so UI renders before Gecko engine starts.
        mainHandler.post {
            try { GeckoRuntimeManager.init(applicationContext) }
            catch (e: Exception) {
                Log.e("AwebApp", "GeckoRuntime init failed: ${e.message}", e)
                mainHandler.postDelayed({
                    try { GeckoRuntimeManager.init(applicationContext) }
                    catch (e2: Exception) { Log.e("AwebApp", "GeckoRuntime retry: ${e2.message}") }
                }, 1500)
            }
            mainHandler.postDelayed({
                try { ServiceHealthWorker.schedule(applicationContext) }
                catch (e: Exception) { Log.w("AwebApp", "WorkManager: ${e.message}") }
            }, 2000)
        }
    }

    private fun installExceptionLogger() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AWEB_CRASH", "CRASH thread=${thread.name} ${throwable.javaClass.name}: ${throwable.message}")
            throwable.stackTrace.take(30).forEach { Log.e("AWEB_CRASH", "  at $it") }
            throwable.cause?.let { c ->
                Log.e("AWEB_CRASH", "Caused by: ${c.javaClass.name}: ${c.message}")
                c.stackTrace.take(10).forEach { Log.e("AWEB_CRASH", "  at $it") }
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ch = NotificationChannel(
                    getString(R.string.notification_channel_id),
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
            } catch (e: Exception) { Log.e("AwebApp", "channel: ${e.message}") }
        }
    }
}

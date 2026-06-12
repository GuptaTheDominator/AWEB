package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.aweb.browser.gecko.GeckoRuntimeManager
import com.aweb.browser.lifecycle.MemoryPressureReceiver
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.service.ServiceHealthWorker
import com.aweb.browser.service.ServiceManager
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class AwebApplication : Application(), Configuration.Provider {

    @Inject lateinit var lifecycleManager: TabLifecycleManager
    @Inject lateinit var serviceManager: ServiceManager
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val mainHandler = Handler(Looper.getMainLooper())

    override val workManagerConfiguration: Configuration
        get() = try {
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.INFO)
                .build()
        } catch (e: UninitializedPropertyAccessException) {
            Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
        }

    override fun onCreate() {
        super.onCreate()

        // ──────────────────────────────────────────────────────────────────
        // CRITICAL: AwebApplication.onCreate() is called for EVERY process,
        // including GeckoView child processes (com.aweb.browser:tab0, :gpu,
        // :socket, etc.).
        //
        // Only the MAIN process needs GeckoRuntime, the foreground service,
        // WorkManager, and memory callbacks. Child processes are managed by
        // GeckoView internally and must be left alone.
        //
        // Calling GeckoRuntime.create() in a child process throws:
        //   IllegalStateException: Failed to initialize GeckoRuntime
        //   (GeckoThread already launched)
        // because the main process already started GeckoRuntime.
        // ──────────────────────────────────────────────────────────────────
        if (!isMainProcess()) {
            Log.d("AwebApp", "Child process ${getProcessName()} — skipping main-process init")
            return
        }

        Log.i("AwebApp", "Main process init starting")
        installExceptionLogger()
        createNotificationChannel()

        // Start foreground service IMMEDIATELY so oom_score_adj is elevated
        // before GeckoRuntime spins up (which causes RAM spike and LMKD kill)
        try {
            serviceManager.startService(this)
            Log.i("AwebApp", "Foreground service started — process priority elevated")
        } catch (e: Exception) {
            Log.e("AwebApp", "Service start failed: ${e.message}")
        }

        // Register memory pressure callbacks
        try {
            registerComponentCallbacks(MemoryPressureReceiver(
                lifecycleManager,
                { AppState.currentTabs },
                { AppState.currentWorkspace }
            ))
        } catch (e: Exception) {
            Log.e("AwebApp", "Callbacks: ${e.message}")
        }

        // GeckoRuntime.create() MUST run on the Main thread.
        // Deferred via Handler so the first Activity frame renders first.
        mainHandler.post {
            try {
                GeckoRuntimeManager.init(applicationContext)
            } catch (e: Exception) {
                Log.e("AwebApp", "GeckoRuntime init failed: ${e.message}", e)
                mainHandler.postDelayed({
                    try { GeckoRuntimeManager.init(applicationContext) }
                    catch (e2: Exception) { Log.e("AwebApp", "GeckoRuntime retry: ${e2.message}") }
                }, 1500)
            }
            // Schedule WorkManager after GeckoRuntime is initialized
            mainHandler.postDelayed({
                try { ServiceHealthWorker.schedule(applicationContext) }
                catch (e: Exception) { Log.w("AwebApp", "WorkManager: ${e.message}") }
            }, 2000)
        }
    }

    /**
     * Returns true if we are running in the main application process.
     * GeckoView child processes have names like "com.aweb.browser:tab0",
     * "com.aweb.browser:gpu", "com.aweb.browser:socket", etc.
     * The main process name equals the package name with no suffix.
     */
    private fun isMainProcess(): Boolean {
        return try {
            val processName = getProcessName()
            processName == packageName
        } catch (e: Exception) {
            // If we can't determine, assume main process
            true
        }
    }

    private fun getProcessName(): String {
        // API 28+: Application.getProcessName() is available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        // API < 28: read from /proc
        return try {
            File("/proc/${Process.myPid()}/cmdline")
                .readText()
                .trim()
                .trimEnd('\u0000')
        } catch (e: Exception) {
            packageName
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
                getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(ch)
            } catch (e: Exception) {
                Log.e("AwebApp", "Channel: ${e.message}")
            }
        }
    }
}

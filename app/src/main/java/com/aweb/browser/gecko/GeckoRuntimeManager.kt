package com.aweb.browser.gecko

import android.content.Context
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Singleton holder for the one [GeckoRuntime] per process.
 *
 * CRITICAL: GeckoRuntime.create() MUST be called on the MAIN THREAD.
 * Calling it from any background thread causes an immediate crash:
 *   IllegalStateException: Must be called from the main thread
 *
 * All callers must ensure they are on the main thread before calling [init] or [getOrInit].
 * [GeckoSessionWrapper.open] dispatches to Main before calling this.
 */
object GeckoRuntimeManager {

    private const val TAG = "GeckoRuntimeManager"

    @Volatile
    private var _runtime: GeckoRuntime? = null

    val runtime: GeckoRuntime
        get() = _runtime ?: error("GeckoRuntimeManager not initialised — call init() on Main thread first")

    val isInitialised: Boolean
        get() = _runtime != null

    /**
     * Initialise GeckoRuntime. MUST be called on the Main thread.
     * Safe to call multiple times — only runs once.
     */
    fun init(context: Context) {
        if (_runtime != null) return

        // Enforce main-thread requirement
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e(TAG, "init() called from background thread — posting to Main thread")
            android.os.Handler(Looper.getMainLooper()).post { init(context) }
            return
        }

        synchronized(this) {
            if (_runtime != null) return

            val contentBlockingSettings = ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                .build()

            val settings = GeckoRuntimeSettings.Builder()
                .contentBlocking(contentBlockingSettings)
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(false)
                .build()

            _runtime = GeckoRuntime.create(context.applicationContext, settings)
            Log.i(TAG, "GeckoRuntime initialised on thread: ${Thread.currentThread().name}")
        }
    }

    /**
     * Get or initialise the runtime.
     * MUST be called on the Main thread.
     * Returns null if not yet initialised (caller should retry).
     */
    fun getOrInit(context: Context): GeckoRuntime? {
        if (_runtime != null) return _runtime

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "getOrInit() called from background thread '${Thread.currentThread().name}' — returning null, will init on Main")
            android.os.Handler(Looper.getMainLooper()).post { init(context) }
            return null   // caller must retry
        }

        init(context)
        return _runtime
    }
}

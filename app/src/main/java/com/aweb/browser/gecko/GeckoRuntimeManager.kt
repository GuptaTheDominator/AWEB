package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Singleton holder for the single [GeckoRuntime] instance.
 *
 * GeckoView requires exactly ONE runtime per process — creating more than one
 * will throw. We initialise it here and expose it app-wide.
 *
 * Phase 1: single runtime, no context isolation yet.
 * Phase 2: context isolation added via GeckoSession contextId.
 */
object GeckoRuntimeManager {

    private const val TAG = "GeckoRuntimeManager"

    @Volatile
    private var _runtime: GeckoRuntime? = null

    val runtime: GeckoRuntime
        get() = _runtime ?: error("GeckoRuntimeManager.init() was not called.")

    /**
     * Must be called once from [AwebApplication.onCreate] before any
     * GeckoSession is created.
     */
    fun init(context: Context) {
        if (_runtime != null) return
        synchronized(this) {
            if (_runtime != null) return

            val settings = GeckoRuntimeSettings.Builder()
                // Security: block insecure mixed content
                .contentBlocking(
                    org.mozilla.geckoview.ContentBlocking.Settings.Builder()
                        .antiTracking(org.mozilla.geckoview.ContentBlocking.AntiTracking.DEFAULT)
                        .build()
                )
                // Allow JavaScript (essential for modern web apps)
                .javaScriptEnabled(true)
                // Remote debugging via USB — useful during development
                .remoteDebuggingEnabled(true)
                // Crash handler telemetry — off for personal use
                .crashHandler(null)
                .build()

            _runtime = GeckoRuntime.create(context.applicationContext, settings)
            Log.i(TAG, "GeckoRuntime initialised")
        }
    }
}

package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Singleton holder for the one [GeckoRuntime] per process.
 *
 * Called from [AwebApplication] after the first frame to avoid blocking the launcher.
 * Any code that accesses [runtime] must ensure [init] has already completed —
 * [GeckoSessionWrapper.open] does this by calling [init] lazily as a safety net.
 */
object GeckoRuntimeManager {

    private const val TAG = "GeckoRuntimeManager"

    @Volatile
    private var _runtime: GeckoRuntime? = null

    val runtime: GeckoRuntime
        get() = _runtime ?: error("GeckoRuntimeManager.init() was not called.")

    val isInitialised: Boolean
        get() = _runtime != null

    fun init(context: Context) {
        if (_runtime != null) return
        synchronized(this) {
            if (_runtime != null) return

            val contentBlockingSettings = ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                .build()

            val settings = GeckoRuntimeSettings.Builder()
                .contentBlocking(contentBlockingSettings)
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(false)   // disable in release — saves resources
                .build()

            _runtime = GeckoRuntime.create(context.applicationContext, settings)
            Log.i(TAG, "GeckoRuntime initialised")
        }
    }

    /** Lazily init + return runtime — safe to call from GeckoSessionWrapper.open() */
    fun getOrInit(context: Context): GeckoRuntime {
        if (_runtime == null) init(context)
        return _runtime!!
    }
}

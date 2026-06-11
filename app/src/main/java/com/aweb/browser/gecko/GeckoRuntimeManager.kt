package com.aweb.browser.gecko

import android.content.Context
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeManager {
    private const val TAG = "GeckoRuntimeMgr"
    @Volatile private var _runtime: GeckoRuntime? = null
    val runtime: GeckoRuntime get() = _runtime ?: error("GeckoRuntimeManager not initialised")
    val isInitialised: Boolean get() = _runtime != null

    fun init(context: Context) {
        if (_runtime != null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "init() off-main — posting to Main")
            android.os.Handler(Looper.getMainLooper()).post { init(context) }
            return
        }
        synchronized(this) {
            if (_runtime != null) return
            val cb = ContentBlocking.Settings.Builder().antiTracking(ContentBlocking.AntiTracking.DEFAULT).build()
            val settings = GeckoRuntimeSettings.Builder().contentBlocking(cb).javaScriptEnabled(true).remoteDebuggingEnabled(false).build()
            _runtime = GeckoRuntime.create(context.applicationContext, settings)
            Log.i(TAG, "GeckoRuntime ready on ${Thread.currentThread().name}")
        }
    }

    fun getOrInit(context: Context): GeckoRuntime? {
        if (_runtime != null) return _runtime
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "getOrInit() off-main — posting init to Main, returning null")
            android.os.Handler(Looper.getMainLooper()).post { init(context) }
            return null
        }
        init(context)
        return _runtime
    }
}

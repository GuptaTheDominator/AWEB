@file:Suppress("DEPRECATION")
package com.aweb.browser.lifecycle


import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity

/**
 * Implements Android's [ComponentCallbacks2] so the app can react to
 * system memory pressure signals.
 *
 * Register this with [Application.registerComponentCallbacks] once at startup.
 * The app provides the current tab/workspace snapshot via [onTrimMemory].
 *
 * [TabLifecycleManager.onMemoryPressure] is called with the Android level
 * so it can decide what to unload.
 */
class MemoryPressureReceiver(
    private val lifecycleManager  : TabLifecycleManager,
    private val tabsSnapshot      : () -> List<TabEntity>,
    private val workspaceSnapshot : () -> WorkspaceEntity?,
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "MemoryPressureReceiver"
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "onTrimMemory level=$level")
        val workspace = workspaceSnapshot() ?: return
        val tabs      = tabsSnapshot()
        lifecycleManager.onMemoryPressure(level, tabs, workspace)
    }

    override fun onConfigurationChanged(newConfig: Configuration) { /* no-op */ }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory — treating as TRIM_MEMORY_COMPLETE")
        val workspace = workspaceSnapshot() ?: return
        val tabs      = tabsSnapshot()
        lifecycleManager.onMemoryPressure(
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE, tabs, workspace
        )
    }
}

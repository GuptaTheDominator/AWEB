package com.aweb.browser.service

import android.content.Context

/**
 * Small persistent switch for the survival foreground service.
 *
 * Default is enabled. Tapping the notification Exit action disables it and
 * cancels the health worker so the app does not immediately resurrect a
 * service the user explicitly stopped. Opening the app again enables it.
 */
object ServicePreferences {
    private const val PREFS = "aweb_service_prefs"
    private const val KEY_ENABLED = "foreground_service_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}

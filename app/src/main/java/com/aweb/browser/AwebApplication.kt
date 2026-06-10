package com.aweb.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aweb.browser.gecko.GeckoRuntimeManager
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry-point.
 *
 * Responsibilities:
 *  - Hilt dependency injection graph root
 *  - GeckoView runtime warm-up (must happen before any Activity draws)
 *  - Notification channel registration for the foreground service
 */
@HiltAndroidApp
class AwebApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Warm up GeckoView runtime as early as possible so the first
        // GeckoSession creation is fast when MainActivity opens.
        GeckoRuntimeManager.init(this)

        // Register notification channel (required on API 26+)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW   // silent, persistent
            ).apply {
                description = "Keeps AWEB workspaces and Keep Alive tabs running"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}

package com.aweb.browser.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aweb.browser.AppState
import com.aweb.browser.R
import com.aweb.browser.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Persistent foreground service.
 *
 * Keeps the app process at elevated priority so Android is less likely
 * to kill it under memory pressure.
 *
 * Deliberately minimal: no @Inject fields beyond what is needed.
 * All heavy logic lives in ViewModels / TabLifecycleManager.
 */
@AndroidEntryPoint
class AwebForegroundService : Service() {

    companion object {
        private const val TAG         = "AwebForegroundService"
        private const val NOTIF_ID    = 1001
        const val ACTION_UPDATE_NOTIF = "com.aweb.browser.ACTION_UPDATE_NOTIF"
        const val ACTION_STOP_SERVICE = "com.aweb.browser.ACTION_STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_NOTIF -> {
                try { updateNotification() } catch (e: Exception) { /* non-fatal */ }
            }
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Stop requested")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                try {
                    startForeground(NOTIF_ID, buildNotification())
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground in onStartCommand failed: ${e.message}")
                }
                Log.i(TAG, "Service (re)started")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed")
        ServiceHealthWorker.schedule(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy — START_STICKY will restart")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val tabs      = AppState.currentTabs
        val keepAlive = tabs.count { it.keepAlive }
        val total     = tabs.size

        val text = when {
            keepAlive > 0 -> "$keepAlive Keep Alive tab${if (keepAlive > 1) "s" else ""} running • $total total"
            total > 0     -> "$total tab${if (total > 1) "s" else ""} open"
            else          -> "Ready"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIF_ID, buildNotification())
    }
}

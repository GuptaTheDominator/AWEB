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
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service — keeps the AWEB process alive at elevated priority.
 *
 * Phase 7 responsibilities:
 *  - Runs as a foreground service with a persistent silent notification.
 *  - Shows Keep Alive tab count in the notification so the user can see
 *    at a glance that their sessions are being maintained.
 *  - Updates the notification when tab state changes (called from TabViewModel).
 *  - Provides START_STICKY so Android restarts us if we are killed.
 *
 * What it does NOT do:
 *  - Does not blindly keep every tab loaded (that is TabLifecycleManager's job).
 *  - Does not do network work itself.
 *  - Does not wake the CPU on a schedule (WorkManager handles health checks).
 */
@AndroidEntryPoint
class AwebForegroundService : Service() {

    @Inject lateinit var lifecycleManager: TabLifecycleManager

    companion object {
        private const val TAG          = "AwebForegroundService"
        private const val NOTIF_ID     = 1001
        const val ACTION_UPDATE_NOTIF  = "com.aweb.browser.ACTION_UPDATE_NOTIF"
        const val ACTION_STOP_SERVICE  = "com.aweb.browser.ACTION_STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_NOTIF -> updateNotification()
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Explicit stop requested")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Normal start — ensure foreground is active
                startForeground(NOTIF_ID, buildNotification())
                Log.i(TAG, "Service started/restarted")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped AWEB from recents — restart ourselves
        Log.w(TAG, "Task removed — scheduling restart via ServiceHealthWorker")
        ServiceHealthWorker.schedule(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.w(TAG, "Service destroyed — will be restarted by START_STICKY or WorkManager")
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val tabs       = AppState.currentTabs
        val keepAlive  = tabs.count { it.keepAlive }
        val totalTabs  = tabs.size

        val contentText = when {
            keepAlive > 0  -> "$keepAlive Keep Alive tab${if (keepAlive > 1) "s" else ""} running  •  $totalTabs total"
            totalTabs > 0  -> "$totalTabs tab${if (totalTabs > 1) "s" else ""} open"
            else           -> "Ready"
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
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification())
    }
}

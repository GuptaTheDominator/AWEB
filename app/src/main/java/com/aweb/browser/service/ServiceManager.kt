package com.aweb.browser.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central helper for starting/stopping [AwebForegroundService] from anywhere
 * in the app without scattering intent-building logic across files.
 *
 * Injected into [AwebApplication] and [MainActivity] to manage lifecycle.
 */
@Singleton
class ServiceManager @Inject constructor() {

    companion object {
        private const val TAG = "ServiceManager"
    }

    fun startService(context: Context) {
        val intent = Intent(context, AwebForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}")
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, AwebForegroundService::class.java).apply {
            action = AwebForegroundService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
        Log.i(TAG, "Stop signal sent to foreground service")
    }

    fun requestNotificationUpdate(context: Context) {
        val intent = Intent(context, AwebForegroundService::class.java).apply {
            action = AwebForegroundService.ACTION_UPDATE_NOTIF
        }
        try {
            // Use startService (not startForegroundService) for notification-only updates.
            // startForegroundService throws ForegroundServiceStartNotAllowedException on
            // API 34+ when called from the background. Since the service is already running
            // as a foreground service (started in Application.onCreate), startService is
            // sufficient — it delivers the ACTION_UPDATE_NOTIF intent to onStartCommand.
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Notification update failed: ${e.message}")
        }
    }
}

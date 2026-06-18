package com.aweb.browser.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receives BOOT_COMPLETED and MY_PACKAGE_REPLACED.
 *
 * On boot, starts the foreground service so AWEB's session monitoring
 * begins before the user opens the app. WorkManager health checks are
 * also re-scheduled here since WorkManager tasks do not always survive
 * a reboot without this.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        if (!ServicePreferences.isEnabled(context)) {
            Log.i(TAG, "Received: $action — survival service disabled, skipping restart")
            runCatching { ServiceHealthWorker.cancel(context) }
            return
        }

        Log.i(TAG, "Received: $action — starting foreground service and health worker")

        // Start foreground service. Some Android/HyperOS builds can still reject
        // background FGS starts after boot/package replace; never crash the receiver.
        try {
            val serviceIntent = Intent(context, AwebForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start from $action failed: ${e.message}")
        }

        // Re-schedule WorkManager health check even if immediate FGS start failed.
        try { ServiceHealthWorker.schedule(context) }
        catch (e: Exception) { Log.w(TAG, "Health worker schedule failed: ${e.message}") }
    }
}

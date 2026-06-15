package com.aweb.browser.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.aweb.browser.AppState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager task that checks whether [AwebForegroundService] is
 * still running and restarts it if not.
 *
 * Runs every 15 minutes (the minimum WorkManager interval).
 *
 * Also updates the foreground service notification with the latest
 * Keep Alive count so it stays accurate even if the Activity is not open.
 *
 * WorkManager survives:
 *  - App process death
 *  - Device reboot (work is re-enqueued by BootReceiver)
 *  - Battery saver mode (constraints permitting)
 */
@HiltWorker
class ServiceHealthWorker @AssistedInject constructor(
    @Assisted context : Context,
    @Assisted params  : WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG       = "ServiceHealthWorker"
        private const val WORK_NAME = "aweb_service_health"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceHealthWorker>(
                repeatInterval    = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,   // update the existing schedule
                request,
            )
            Log.i(TAG, "Health worker scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Health check running — keepAlive tabs: ${AppState.currentTabs.count { it.keepAlive }}")

        // Restart the foreground service if it is not running
        ensureServiceRunning()

        // Request a notification update so the count stays fresh
        val updateIntent = Intent(applicationContext, AwebForegroundService::class.java).apply {
            action = AwebForegroundService.ACTION_UPDATE_NOTIF
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(updateIntent)
            } else {
                applicationContext.startService(updateIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not send update intent: ${e.message}")
        }

        return Result.success()
    }

    private fun ensureServiceRunning() {
        try {
            val serviceIntent = Intent(applicationContext, AwebForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
            Log.d(TAG, "Service start/restart requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}")
        }
    }
}

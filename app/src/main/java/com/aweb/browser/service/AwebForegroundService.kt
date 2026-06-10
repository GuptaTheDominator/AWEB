package com.aweb.browser.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground service — stub for Phase 1.
 *
 * Phase 7 will complete this with:
 *  - Persistent notification
 *  - Keep Alive tab monitoring
 *  - Session health checks via WorkManager
 *
 * Declared in AndroidManifest so the app compiles from Phase 1 onward.
 */
class AwebForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AwebForegroundService", "Service started — Phase 7 implementation pending")
        return START_STICKY
    }
}

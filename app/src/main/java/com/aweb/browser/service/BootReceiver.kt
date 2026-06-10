package com.aweb.browser.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot receiver — stub for Phase 1.
 *
 * Phase 7 will restart AWEB or its foreground service after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Boot/update received — Phase 7 restart logic pending")
            // Phase 7: launch AwebForegroundService or MainActivity
        }
    }
}

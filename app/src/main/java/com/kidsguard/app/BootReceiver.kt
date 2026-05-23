package com.kidsguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts KidsGuard after the phone is rebooted,
 * so parents don't have to remember to start it manually.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FaceWatchService::class.java)
            )
        }
    }
}

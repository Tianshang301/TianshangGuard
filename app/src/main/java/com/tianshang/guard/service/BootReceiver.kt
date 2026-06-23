package com.tianshang.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tianshang.guard.data.local.GuardPreferences

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = GuardPreferences(context)
            if (prefs.isBootStartEnabled()) {
                val serviceIntent = Intent(context, ForegroundService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}

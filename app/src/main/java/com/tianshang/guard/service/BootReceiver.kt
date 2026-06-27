package com.tianshang.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tianshang.guard.data.local.GuardPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // H-17: Use DataStore with timeout instead of SharedPreferences
            val bootStart = runBlocking {
                withTimeoutOrNull(2000L) {
                    GuardPreferences(context).bootStart.first()
                }
            } ?: true // Default to true if timeout
            if (bootStart) {
                val vpnIntent = Intent(context, GuardVpnService::class.java).apply {
                    action = GuardVpnService.ACTION_START
                }
                context.startForegroundService(vpnIntent)
            }
        }
    }
}

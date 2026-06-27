package com.tianshang.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tianshang.guard.data.local.GuardPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // M-11: Use SharedPreferences directly to avoid runBlocking on main thread
            // and to avoid creating a second DataStore instance
            val sp = context.getSharedPreferences("guard_preferences", Context.MODE_PRIVATE)
            val bootStart = sp.getBoolean("boot_start", true)
            if (bootStart) {
                val vpnIntent = Intent(context, GuardVpnService::class.java).apply {
                    action = GuardVpnService.ACTION_START
                }
                context.startForegroundService(vpnIntent)
            }
        }
    }
}

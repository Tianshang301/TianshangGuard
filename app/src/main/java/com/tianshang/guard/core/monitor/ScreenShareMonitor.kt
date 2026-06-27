package com.tianshang.guard.core.monitor

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tianshang.guard.core.alert.AlertEngine

class ScreenShareMonitor(
    private val context: Context,
    private val alertEngine: AlertEngine,
    private val configProvider: RemoteConfigProvider
) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as UsageStatsManager

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var isMonitoring = false

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            checkForegroundApps()
            handler.postDelayed(this, 3000)
        }
    }

    private fun checkForegroundApps() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var foregroundApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foregroundApp = event.packageName
            }
        }

        if (foregroundApp != null && isBankApp(foregroundApp)) {
            if (isAnyScreenShareAppRunning()) {
                alertEngine.showScreenShareWarning()
            }
        }
    }

    private fun isBankApp(packageName: String): Boolean {
        return configProvider.bankApps.contains(packageName)
    }

    private fun isAnyScreenShareAppRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
        val processes = activityManager.runningAppProcesses ?: return false
        return processes.any { configProvider.screenShareApps.contains(it.processName) }
    }
}

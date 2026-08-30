package com.tianshang.guard.core.monitor

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
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
            if (isAnyScreenShareAppRunning() || isScreenCaptureActive()) {
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
        return processes.any { p ->
            configProvider.screenShareApps.contains(p.processName) && isProcessOwnerOfPackage(p)
        }
    }

    /**
     * B3 (M-01): reject forged process names. A malicious app could set
     * android:process to a known screen-share package; verify the process's UID
     * actually belongs to that package before treating it as a real remote-access
     * app.
     */
    private fun isProcessOwnerOfPackage(process: ActivityManager.RunningAppProcessInfo): Boolean {
        val packageName = process.processName ?: return false
        val packageManager = context.packageManager
        return try {
            val owner = packageManager.getNameForUid(process.uid) ?: return false
            owner == packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * B3 (M-01): additive signal — if the user has an active MediaProjection
     * (screen capture) session, treat it as suspicious when a banking app is in
     * the foreground. API 34+ only; accessed via reflection so this class stays
     * compatible with minSdk 26 without a direct API-34 method dependency.
     */
    private fun isScreenCaptureActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            val mpm = context.getSystemService(MediaProjectionManager::class.java) ?: return false
            val active = mpm.javaClass.getMethod("getActiveProjection").invoke(mpm)
            active != null
        } catch (_: Exception) {
            false
        }
    }
}

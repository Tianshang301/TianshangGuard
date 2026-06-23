package com.tianshang.guard.domain

import com.tianshang.guard.core.monitor.ScreenShareMonitor

class DetectScreenSharingUseCase(
    private val monitor: ScreenShareMonitor
) {
    fun execute() {
        monitor.startMonitoring()
    }

    fun stop() {
        monitor.stopMonitoring()
    }
}

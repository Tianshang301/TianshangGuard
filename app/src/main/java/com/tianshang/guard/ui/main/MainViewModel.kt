package com.tianshang.guard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.monitor.ScreenShareMonitor
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.repository.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class MainViewModel(
    private val dnsEngine: DnsEngine,
    private val monitor: ScreenShareMonitor,
    private val prefs: GuardPreferences,
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _vpnRunning = MutableStateFlow(false)
    val vpnRunning: StateFlow<Boolean> = _vpnRunning.asStateFlow()

    val blockedCount: StateFlow<Int> = combine(
        alertRepository.getCountByTypeFlow("BLACKLIST_BLOCKED"),
        alertRepository.getCountByTypeFlow("SUSPICIOUS_DOMAIN")
    ) { blocked, suspicious -> blocked + suspicious }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val visitedCount: StateFlow<Int> = alertRepository.getCountByTypeFlow("VISITED")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val behaviorCount: StateFlow<Int> = alertRepository.getCountByTypeFlow("SCREEN_SHARE")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    fun setVpnRunning(running: Boolean) {
        _vpnRunning.value = running
    }

    fun toggleMonitoring() {
        if (_monitoring.value) {
            monitor.stopMonitoring()
            _monitoring.value = false
        } else {
            monitor.startMonitoring()
            _monitoring.value = true
        }
    }
}

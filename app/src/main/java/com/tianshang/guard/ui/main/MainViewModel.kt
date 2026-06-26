package com.tianshang.guard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.monitor.ScreenShareMonitor
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.repository.AlertRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(
    private val dnsEngine: DnsEngine,
    private val monitor: ScreenShareMonitor,
    private val prefs: GuardPreferences,
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _vpnRunning = MutableStateFlow(false)
    val vpnRunning: StateFlow<Boolean> = _vpnRunning.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0L)
    private var lastDate = LocalDate.MIN

    init {
        viewModelScope.launch {
            lastDate = LocalDate.now()
            while (true) {
                delay(60_000L)
                val today = LocalDate.now()
                if (today != lastDate) {
                    lastDate = today
                    _refreshTrigger.value = System.currentTimeMillis()
                }
            }
        }
    }

    val blockedToday: StateFlow<Int> = _refreshTrigger.flatMapLatest {
        val since = AlertRepository.todayStartMs()
        combine(
            alertRepository.getCountByTypeSinceFlow("BLACKLIST_BLOCKED", since),
            alertRepository.getCountByTypeSinceFlow("SUSPICIOUS_DOMAIN", since)
        ) { blocked, suspicious -> blocked + suspicious }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val visitedToday: StateFlow<Int> = _refreshTrigger.flatMapLatest {
        alertRepository.getCountByTypeSinceFlow("VISITED", AlertRepository.todayStartMs())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val behaviorToday: StateFlow<Int> = _refreshTrigger.flatMapLatest {
        alertRepository.getCountByTypeSinceFlow("SCREEN_SHARE", AlertRepository.todayStartMs())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

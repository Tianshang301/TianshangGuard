package com.tianshang.guard.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    val blockedCount: StateFlow<Int> = combine(
        alertRepository.getCountByTypeFlow("BLACKLIST_BLOCKED"),
        alertRepository.getCountByTypeFlow("SUSPICIOUS_DOMAIN")
    ) { blocked, suspicious -> blocked + suspicious }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val analyzedCount: StateFlow<Int> = alertRepository.getCountByTypeFlow("PHISHING_PAGE")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val behaviorCount: StateFlow<Int> = alertRepository.getCountByTypeFlow("SCREEN_SHARE")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val visitedCount: StateFlow<Int> = alertRepository.getCountByTypeFlow("VISITED")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val smsCount: StateFlow<Int> = alertRepository.getCountByTypeFlow("SMS_PHISHING")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val recentAlerts: Flow<List<AlertEntity>> = alertRepository.getRecentAlerts(100)
}

package com.tianshang.guard.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.repository.AlertRepository
import com.tianshang.guard.data.repository.TimeRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.LAST_24H)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()

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

    fun selectRange(range: TimeRange) {
        _selectedRange.value = range
    }

    private val rangeSignal = combine(_selectedRange, _refreshTrigger) { range, _ -> range }

    val blockedCount: StateFlow<Int> = rangeSignal.flatMapLatest { range ->
        val since = AlertRepository.sinceMs(range)
        combine(
            alertRepository.getCountByTypeSinceFlow("BLACKLIST_BLOCKED", since),
            alertRepository.getCountByTypeSinceFlow("SUSPICIOUS_DOMAIN", since)
        ) { blocked, suspicious -> blocked + suspicious }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val visitedCount: StateFlow<Int> = rangeSignal.flatMapLatest { range ->
        alertRepository.getCountByTypeSinceFlow("VISITED", AlertRepository.sinceMs(range))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val behaviorCount: StateFlow<Int> = rangeSignal.flatMapLatest { range ->
        alertRepository.getCountByTypeSinceFlow("SCREEN_SHARE", AlertRepository.sinceMs(range))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val smsCount: StateFlow<Int> = rangeSignal.flatMapLatest { range ->
        alertRepository.getCountByTypeSinceFlow("SMS_PHISHING", AlertRepository.sinceMs(range))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentAlerts: Flow<List<AlertEntity>> = alertRepository.getRecentAlerts(100)
}

package com.tianshang.guard.data.repository

import com.tianshang.guard.data.local.database.AlertDao
import com.tianshang.guard.data.local.database.AlertEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AlertRepository(private val alertDao: AlertDao) {

    suspend fun insert(alert: AlertEntity) {
        alertDao.insert(alert)
    }

    fun getRecentAlerts(limit: Int = 50): Flow<List<AlertEntity>> {
        return alertDao.getRecentAlerts(limit)
    }

    fun getBlockedCount(): Int {
        return alertDao.getBlockedCount()
    }

    fun getCountByType(type: String): Int {
        return alertDao.getCountByType(type)
    }

    fun getCountByTypeFlow(type: String): Flow<Int> {
        return alertDao.getCountByTypeFlow(type)
    }

    fun getCountByTypeSinceFlow(type: String, since: Long): Flow<Int> {
        return alertDao.getCountByTypeSinceFlow(type, since)
    }

    suspend fun getCountByTypeSince(type: String, since: Long): Int {
        return alertDao.getCountByTypeSince(type, since)
    }

    fun getRecentAlertsSync(limit: Int = 1000): List<AlertEntity> {
        return alertDao.getRecentAlertsSync(limit)
    }

    fun getAlertsAscSync(limit: Int = 1000): List<AlertEntity> {
        return alertDao.getAlertsAscSync(limit)
    }

    suspend fun clearAll() {
        alertDao.clearAll()
    }

    companion object {
        fun todayStartMs(): Long {
            return LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        fun sinceMs(range: TimeRange): Long = when (range) {
            TimeRange.LAST_24H -> System.currentTimeMillis() - 86_400_000L
            TimeRange.LAST_7D -> System.currentTimeMillis() - 7 * 86_400_000L
            TimeRange.LAST_30D -> System.currentTimeMillis() - 30 * 86_400_000L
            TimeRange.LAST_365D -> System.currentTimeMillis() - 365 * 86_400_000L
            TimeRange.TOTAL -> 0L
        }
    }
}

enum class TimeRange(val labelRes: Int) {
    LAST_24H(com.tianshang.guard.R.string.stats_range_24h),
    LAST_7D(com.tianshang.guard.R.string.stats_range_7d),
    LAST_30D(com.tianshang.guard.R.string.stats_range_30d),
    LAST_365D(com.tianshang.guard.R.string.stats_range_365d),
    TOTAL(com.tianshang.guard.R.string.stats_range_total)
}

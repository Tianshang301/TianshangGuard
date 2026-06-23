package com.tianshang.guard.data.repository

import com.tianshang.guard.data.local.database.AlertDao
import com.tianshang.guard.data.local.database.AlertEntity
import kotlinx.coroutines.flow.Flow

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

    fun getRecentAlertsSync(limit: Int = 1000): List<AlertEntity> {
        return alertDao.getRecentAlertsSync(limit)
    }

    fun getAlertsAscSync(limit: Int = 1000): List<AlertEntity> {
        return alertDao.getAlertsAscSync(limit)
    }

    suspend fun clearAll() {
        alertDao.clearAll()
    }
}

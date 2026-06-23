package com.tianshang.guard.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert
    suspend fun insert(alert: AlertEntity)

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlerts(limit: Int): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE type = 'BLACKLIST_BLOCKED'")
    fun getBlockedCount(): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type")
    fun getCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type")
    fun getCountByTypeFlow(type: String): Flow<Int>

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlertsSync(limit: Int): List<AlertEntity>

    @Query("SELECT * FROM alerts ORDER BY timestamp ASC LIMIT :limit")
    fun getAlertsAscSync(limit: Int): List<AlertEntity>

    @Query("DELETE FROM alerts")
    suspend fun clearAll()
}

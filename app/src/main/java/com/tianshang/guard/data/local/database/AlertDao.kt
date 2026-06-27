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

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT COUNT(*) FROM alerts WHERE type = 'BLACKLIST_BLOCKED'")
    suspend fun getBlockedCount(): Int

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type")
    fun getCountByTypeFlow(type: String): Flow<Int>

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type AND timestamp >= :since")
    suspend fun getCountByTypeSince(type: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type AND timestamp >= :since")
    fun getCountByTypeSinceFlow(type: String, since: Long): Flow<Int>

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAlertsSync(limit: Int): List<AlertEntity>

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT * FROM alerts ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getAlertsAscSync(limit: Int): List<AlertEntity>

    @Query("DELETE FROM alerts")
    suspend fun clearAll()
}

package com.tianshang.guard.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Query("SELECT * FROM domains WHERE category = 'WHITELIST'")
    fun getWhitelist(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE category = 'BLACKLIST'")
    fun getBlacklist(): Flow<List<DomainEntity>>

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain AND category = 'WHITELIST')")
    suspend fun isWhitelisted(domain: String): Boolean

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain AND category = 'BLACKLIST')")
    suspend fun isBlacklisted(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: DomainEntity)

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    @Query("SELECT domain FROM domains WHERE category IN ('WHITELIST', 'BLACKLIST')")
    suspend fun getKnownDomains(): List<String>

    @Query("DELETE FROM domains")
    suspend fun clearAll()
}

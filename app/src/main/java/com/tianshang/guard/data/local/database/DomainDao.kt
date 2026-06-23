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

    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain AND category = 'WHITELIST')")
    fun isWhitelisted(domain: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain AND category = 'BLACKLIST')")
    fun isBlacklisted(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: DomainEntity)

    @Query("SELECT domain FROM domains WHERE category IN ('WHITELIST', 'BLACKLIST')")
    fun getKnownDomains(): List<String>

    @Query("DELETE FROM domains")
    suspend fun clearAll()
}
